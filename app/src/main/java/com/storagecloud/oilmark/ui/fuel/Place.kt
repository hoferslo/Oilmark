package com.storagecloud.oilmark.ui.fuel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/*
 * Where a fill-up happened. Stored on the record as "latitude,longitude" text (see Extra.LOCATION).
 * Uses the phone's own location service; no map or Google library needed.
 */

/** A spot on the map. */
data class Spot(val latitude: Double, val longitude: Double) {
    /** "46.056946,14.505751" - the form saved on the record. */
    override fun toString(): String = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

    /** Metres to [other], over the earth's surface. */
    fun metresTo(other: Spot): Float {
        val result = FloatArray(1)
        Location.distanceBetween(latitude, longitude, other.latitude, other.longitude, result)
        return result[0]
    }

    companion object {
        /** Reads the "latitude,longitude" text back; null if it isn't that. */
        fun parse(text: String?): Spot? {
            val parts = text?.split(',')?.map { it.trim().toDoubleOrNull() } ?: return null
            val (latitude, longitude) = parts.takeIf { it.size == 2 }?.filterNotNull()?.takeIf { it.size == 2 } ?: return null
            return if (latitude in -90.0..90.0 && longitude in -180.0..180.0) Spot(latitude, longitude) else null
        }
    }
}

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Where the phone is now, or null if it can't tell within 20 seconds (location off,
 * no permission, indoors without signal). A fix from the last two minutes is used straight away.
 */
@SuppressLint("MissingPermission") // checked by hasLocationPermission first
suspend fun currentSpot(context: Context): Spot? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = manager.getProviders(true)
    val provider = listOfNotNull(
        LocationManager.FUSED_PROVIDER.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).firstOrNull { it in providers } ?: return null

    val recent = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time < 2 * 60 * 1000 }
        .minByOrNull { it.accuracy }
    if (recent != null) return Spot(recent.latitude, recent.longitude)

    val fix = withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val cancel = CancellationSignal()
            continuation.invokeOnCancellation { cancel.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager, provider, cancel, ContextCompat.getMainExecutor(context),
            ) { location -> if (continuation.isActive) continuation.resume(location) }
        }
    }
    return fix?.let { Spot(it.latitude, it.longitude) }
}
