package com.storagecloud.oilmark.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** A country the price service knows. [code] is the two-letter one: "SI", "DE". */
data class Country(val code: String, val name: String)

/**
 * Average pump prices in one country, as last fetched. Prices are in the country's own
 * currency ([currency], e.g. "EUR") and never converted to another currency.
 */
@Serializable
data class CountryFuelPrices(
    val code: String,
    val name: String,
    val currency: String,
    val gasoline: Double? = null,
    val diesel: Double? = null,
    /** What one price is for: "liter", "gallon" (US) or "imperial_gallon". */
    val gasolineUnit: String = "liter",
    val dieselUnit: String = "liter",
    /** When this was fetched, in milliseconds since 1970. */
    val fetchedAt: Long = 0,
) {
    /**
     * The price for a vehicle running on [fuel], per the fuel unit the app is set to
     * (so a per-gallon price becomes per-litre when the app shows litres).
     * Null for electric vehicles or when the country has no price for that fuel.
     */
    fun pricePer(units: Units, fuel: FuelType): Double? {
        val (price, unit) = when (fuel) {
            FuelType.Gasoline -> gasoline to gasolineUnit
            FuelType.Diesel -> diesel to dieselUnit
            FuelType.Electric -> return null
        }
        if (price == null) return null
        val appLitres = if (units == Units.US) LITRES_PER_US_GALLON else 1.0
        return (price / litresIn(unit) * appLitres * 1000).roundToInt() / 1000.0
    }

    private fun litresIn(unit: String) = when (unit) {
        "gallon" -> LITRES_PER_US_GALLON
        "imperial_gallon" -> 4.54609
        else -> 1.0
    }
}

// Top-level rather than in a private companion object: @Serializable classes keep their
// serializer in the companion, and a private one can't be reached from other classes at runtime.
private const val LITRES_PER_US_GALLON = 3.785411784

/**
 * Average fuel prices by country from OpenVan.camp's free public API (no key, CC BY 4.0 -
 * the licence requires showing a credit link wherever the prices are shown, see [CREDIT]).
 *
 * Uses its own plain HTTP client on purpose: the LubeLogger login must never be sent here.
 * Only the country code leaves the phone.
 */
object FuelPriceSource {
    const val CREDIT = "OpenVan.camp"
    const val CREDIT_URL = "https://openvan.camp/"

    /** The service asks for at most one request every 10 minutes. */
    const val MIN_REFRESH_MILLIS = 10 * 60 * 1000L

    private const val URL = "https://openvan.camp/api/fuel/prices"
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Current prices for one country. */
    suspend fun prices(countryCode: String): CountryFuelPrices {
        val entry = fetch("$URL?country=${countryCode.lowercase()}").values.firstOrNull()
            ?: throw ApiException("No fuel prices are available for that country")
        return CountryFuelPrices(
            code = entry.code,
            name = entry.name,
            currency = entry.currency,
            gasoline = entry.prices["gasoline"],
            diesel = entry.prices["diesel"],
            gasolineUnit = entry.units["gasoline"] ?: entry.unit,
            dieselUnit = entry.units["diesel"] ?: entry.unit,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Every country with prices, for the picker in settings. */
    suspend fun countries(): List<Country> =
        fetch(URL).values.map { Country(it.code, it.name) }.sortedBy { it.name }

    private suspend fun fetch(url: String): Map<String, Entry> = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("No fuel prices are available for that country")
                json.decodeFromString<Envelope>(response.body.string()).data
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Couldn't get fuel prices from ${CREDIT}", e)
        }
    }

    @Serializable
    private class Envelope(val data: Map<String, Entry> = emptyMap())

    @Serializable
    private class Entry(
        @SerialName("country_code") val code: String = "",
        @SerialName("country_name") val name: String = "",
        val currency: String = "",
        val unit: String = "liter",
        val units: Map<String, String> = emptyMap(),
        val prices: Map<String, Double?> = emptyMap(),
    )
}
