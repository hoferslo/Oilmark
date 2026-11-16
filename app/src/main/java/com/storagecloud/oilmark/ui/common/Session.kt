package com.storagecloud.oilmark.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.Formats
import com.storagecloud.oilmark.data.FuelPriceSource
import com.storagecloud.oilmark.data.LubeLoggerApi
import com.storagecloud.oilmark.data.Settings
import com.storagecloud.oilmark.data.SettingsStore

/*
 * Things every screen needs while connected to a server. Grab them anywhere with
 * `LocalApi.current` / `LocalFormats.current` instead of passing them through every function.
 */

val LocalSettings = staticCompositionLocalOf { Settings() }
val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> { error("Not connected to a server") }

val LocalApi = staticCompositionLocalOf<LubeLoggerApi> { error("Not connected to a server") }
val LocalFormats = staticCompositionLocalOf { Formats() }
val LocalImages = staticCompositionLocalOf<ImageLoader> { error("Not connected to a server") }

/** Makes the server connection described by [settings] available to [content]. */
@Composable
fun Session(settings: Settings, store: SettingsStore, content: @Composable () -> Unit) {
    val server = requireNotNull(settings.server)
    val context = LocalContext.current.applicationContext
    val api = remember(server) { LubeLoggerApi(server) }
    val images = remember(api) {
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { api.http })) }
            .build()
    }
    val formats = remember(settings.units, settings.currency) { Formats(settings.units, settings.currency) }

    // Online fuel prices: fetched when the app opens (and when the switch, country or units change),
    // then written over the petrol and diesel prices in settings.
    LaunchedEffect(settings.onlineFuelPrices, settings.fuelCountry, settings.units) {
        val country = settings.fuelCountry
        if (!settings.onlineFuelPrices || country == null) return@LaunchedEffect
        val cached = settings.countryPrices
        val age = System.currentTimeMillis() - (cached?.fetchedAt ?: 0)
        try {
            // The service asks not to poll faster than this; reuse what we have until then.
            val prices = if (cached != null && age < FuelPriceSource.MIN_REFRESH_MILLIS) {
                cached
            } else {
                FuelPriceSource.prices(country)
            }
            store.saveCountryPrices(prices, settings.units)
        } catch (_: ApiException) {
            // Offline or unknown country: keep whatever was fetched before.
        }
    }
    CompositionLocalProvider(
        LocalSettings provides settings,
        LocalSettingsStore provides store,
        LocalApi provides api,
        LocalImages provides images,
        LocalFormats provides formats,
        content = content,
    )
}
