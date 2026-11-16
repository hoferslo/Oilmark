package com.storagecloud.oilmark.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where the LubeLogger server lives. Leave [username] empty if the server has no login. */
data class Server(val url: String, val username: String = "", val password: String = "")

/** Anything that goes wrong talking to the server. [message] is always fit to show to the user. */
class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A file about to be sent to the server (a photo, usually). */
class Upload(val name: String, val bytes: ByteArray, val mimeType: String = "image/jpeg")

/**
 * The whole LubeLogger REST API in one small class. Every call suspends, runs off the main
 * thread and throws [ApiException] on failure.
 *
 * ```
 * val api = LubeLoggerApi(Server("http://10.0.2.2:8080"))
 * val garage = api.vehicles()
 * val id = api.addRecord(RecordType.Fuel, vehicleId, Record(date = "2026-11-16", ...))
 * ```
 */
class LubeLoggerApi(val server: Server) {

    private val baseUrl: HttpUrl = server.url.trim().toHttpUrlOrNull()
        ?: throw ApiException("\"${server.url}\" is not a valid server address")

    /** Shared with the image loader so photos get the same login as API calls. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // A login-protected server redirects to its login page; treat that as "unauthorized" instead.
        .followRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            if (server.username.isNotEmpty()) {
                request.header("Authorization", Credentials.basic(server.username, server.password))
            }
            chain.proceed(request.build())
        }
        .build()

    // ---- Server ----

    suspend fun whoAmI(): UserInfo = get("api/whoami")

    suspend fun serverInfo(): ServerInfo = get("api/info")

    // ---- Vehicles ----

    /** Every vehicle in the garage, with its dashboard totals. */
    suspend fun vehicles(): List<VehicleInfo> = get("api/vehicle/info")

    suspend fun vehicle(id: Int): VehicleInfo =
        get<List<VehicleInfo>>("api/vehicle/info", "vehicleId" to id).firstOrNull()
            ?: throw ApiException("That vehicle no longer exists")

    /** Returns the new vehicle's id. */
    suspend fun addVehicle(vehicle: VehicleInput): Int =
        send("POST", "api/vehicles/add", body = json.encodeToString(vehicle.copy(id = null)))
            .additionalData?.vehicleId ?: 0

    suspend fun updateVehicle(vehicle: VehicleInput) {
        send("PUT", "api/vehicles/update", body = json.encodeToString(vehicle))
    }

    suspend fun deleteVehicle(id: Int) {
        send("DELETE", "api/vehicles/delete", "id" to id)
    }

    /**
     * Replaces a vehicle's photo.
     *
     * The REST API has no call for this, so it goes the way the LubeLogger website does it:
     * upload the image, then save the whole vehicle with the new image location. Every other
     * field is read from the server first and sent back untouched.
     */
    suspend fun setVehiclePhoto(vehicleId: Int, photo: Upload) {
        val uploaded = call("POST", "Files/HandleFileUpload", body = multipart("file", listOf(photo)))
        val imageLocation = decode { json.decodeFromString<String>(uploaded) }

        // Raw (not culture-invariant) so text fields like the purchase date come back exactly as stored.
        val vehicles = call("GET", "api/vehicles", invariant = false)
        val vehicle = decode { json.decodeFromString<List<JsonObject>>(vehicles) }
            .firstOrNull { it["id"]?.jsonPrimitive?.intOrNull == vehicleId }
            ?: throw ApiException("That vehicle no longer exists")
        val decimalSeparator = serverInfo().decimalSeparator

        val form = FormBody.Builder()
        fun add(name: String, value: JsonElement) {
            when (value) {
                is JsonNull -> {}
                // The website's form expects decimals written the server's way ("1234,5" in some locales).
                is JsonPrimitive -> form.add(
                    name,
                    if (value.isString) value.content else value.content.replace(".", decimalSeparator),
                )
                is JsonArray -> value.forEachIndexed { index, item -> add("$name[$index]", item) }
                is JsonObject -> value.forEach { (key, item) -> add("$name.$key", item) }
            }
        }
        for ((key, value) in vehicle) {
            add(key, if (key == "imageLocation") JsonPrimitive(imageLocation) else value)
        }

        val saved = call("POST", "Vehicle/SaveVehicle", body = form.build())
        if (!decode { json.decodeFromString<OperationResult>(saved) }.success) {
            throw ApiException("The server couldn't save the photo")
        }
    }

    // ---- Records (fuel, service, reminders, ... see RecordType) ----

    suspend fun records(type: RecordType, vehicleId: Int, units: Units = Units.Metric): List<Record> =
        get<List<Record>>(
            "api/vehicle/${type.path}",
            "vehicleId" to vehicleId,
            // Only fuel records care: tells the server which unit to compute fuel economy in.
            "useMPG" to (units != Units.Metric).takeIf { type == RecordType.Fuel },
            "useUKMPG" to (units == Units.UK).takeIf { type == RecordType.Fuel },
        ).map { it.fromServer() }

    suspend fun record(type: RecordType, vehicleId: Int, id: Int): Record =
        get<List<Record>>("api/vehicle/${type.path}", "vehicleId" to vehicleId, "id" to id).firstOrNull()
            ?.fromServer()
            ?: throw ApiException("That ${type.noun} no longer exists")

    /** Returns the new record's id. */
    suspend fun addRecord(type: RecordType, vehicleId: Int, record: Record): Int =
        send(
            "POST", "api/vehicle/${type.path}/add",
            "vehicleId" to vehicleId,
            // Odometer readings also count towards the distance of whatever equipment is fitted.
            // (Always send this one: LubeLogger 1.7.4 crashes if it is left out.)
            "autoIncludeEquipment" to true.takeIf { type == RecordType.Odometer },
            body = json.encodeToString(record),
        ).additionalData?.recordId ?: 0

    suspend fun updateRecord(type: RecordType, record: Record) {
        send("PUT", "api/vehicle/${type.path}/update", body = json.encodeToString(record))
    }

    suspend fun deleteRecord(type: RecordType, id: Int) {
        send("DELETE", "api/vehicle/${type.path}/delete", "id" to id)
    }

    // ---- Files ----

    /** Uploads files and returns them as attachments, ready to put in a record's `files`. */
    suspend fun upload(files: List<Upload>): List<Attachment> {
        if (files.isEmpty()) return emptyList()
        val text = call("POST", "api/documents/upload", body = multipart("documents", files))
        return decode { json.decodeFromString<List<Attachment>>(text) }
    }

    /** Absolute url for a path the server hands out, like a vehicle's `imageLocation`. */
    fun fileUrl(location: String): String =
        (baseUrl.resolve(location) ?: baseUrl).toString()

    // ---- Plumbing ----

    private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, Any?>): T {
        val text = call("GET", path, *query)
        return decode { json.decodeFromString<T>(text) }
    }

    /** Add/update/delete all answer with the same `{success, message}` envelope. */
    private suspend fun send(
        method: String,
        path: String,
        vararg query: Pair<String, Any?>,
        body: String? = null,
    ): OperationResult {
        // Sent as bytes so the header stays exactly "application/json" - LubeLogger only
        // recognises it without the "; charset=utf-8" that OkHttp adds for strings.
        val requestBody = body.orEmpty().toByteArray().toRequestBody(JSON).takeIf { method != "DELETE" }
        val text = call(method, path, *query, body = requestBody)
        val result = decode { json.decodeFromString<OperationResult>(text) }
        if (!result.success) throw ApiException(result.message.ifBlank { "The server rejected the request" })
        return result
    }

    /** One HTTP request; returns the response text. [path] is relative to the server address. */
    private suspend fun call(
        method: String,
        path: String,
        vararg query: Pair<String, Any?>,
        body: RequestBody? = null,
        invariant: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder().addPathSegments(path).apply {
            for ((name, value) in query) if (value != null) addQueryParameter(name, value.toString())
        }.build()
        val request = Request.Builder().url(url).method(method, body)
        // Asks for real numbers and yyyy-MM-dd dates instead of text in the server's locale.
        if (invariant) request.header("culture-invariant", "true")
        try {
            http.newCall(request.build()).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 || response.isRedirect ->
                        throw ApiException("Wrong username or password")
                    response.isSuccessful -> response.body.string()
                    else -> throw ApiException(
                        // Errors usually come with a readable reason, e.g. "Invalid Record Id".
                        runCatching { json.decodeFromString<OperationResult>(response.body.string()).message }
                            .getOrNull()?.takeIf { it.isNotBlank() }
                            ?: "The server answered with an error (${response.code})",
                    )
                }
            }
        } catch (e: IOException) {
            throw ApiException("Can't reach the server at ${server.url}", e)
        }
    }

    private fun multipart(field: String, files: List<Upload>): RequestBody =
        MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            for (file in files) {
                addFormDataPart(field, file.name, file.bytes.toRequestBody(file.mimeType.toMediaType()))
            }
        }.build()

    private inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw ApiException("That address doesn't look like a LubeLogger server", e)
    } catch (e: IllegalArgumentException) {
        throw ApiException("That address doesn't look like a LubeLogger server", e)
    }

    /**
     * For reminders the server sends the user's choice as `userMetric` and its own derived value
     * as `metric`, but expects the choice back in `metric`. Keep just the choice.
     */
    private fun Record.fromServer(): Record = if (userMetric != null) copy(metric = userMetric) else this

    private companion object {
        val JSON = "application/json".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true   // the server may add fields in newer versions
            coerceInputValues = true   // null where we have a default -> use the default
            explicitNulls = false      // don't send fields we have no value for
        }
    }
}
