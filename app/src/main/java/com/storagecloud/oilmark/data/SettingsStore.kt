package com.storagecloud.oilmark.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Everything the app remembers between launches. All of it lives on the phone only. */
data class Settings(
    /** Null until the user connects. */
    val server: Server? = null,
    val units: Units = Units.Metric,
    /** The symbol shown next to money. Only a label: amounts are never converted. */
    val currency: String = "$",
    /** The symbol the server is set to; [currency] equals this unless the user picked another. */
    val serverCurrency: String = "$",
    /** Price per litre/gallon a new fuel-up starts with. Null = start from the last fill-up's price. */
    val petrolPrice: Double? = null,
    /** Same, for diesel vehicles. With online prices on, both are overwritten by each fetch. */
    val dieselPrice: Double? = null,
    /** Whether the app fetches the country's average fuel price from the internet at start. */
    val onlineFuelPrices: Boolean = false,
    /** Two-letter code of the country to fetch prices for, e.g. "SI". */
    val fuelCountry: String? = null,
    /** The prices last fetched for [fuelCountry]; null when switched off or not fetched yet. */
    val countryPrices: CountryFuelPrices? = null,
    /** Whether the quick fuel-up asks for the distance since the last fill-up instead of the odometer. */
    val fuelByTrip: Boolean = false,
    /** Whether the phone shows a notification when a reminder becomes due. */
    val reminderNotifications: Boolean = true,
    /** Whether the app has already asked Android for permission to show notifications. */
    val notificationsAsked: Boolean = false,
    /** The vehicle the app opens straight into, if the user picked one. */
    val defaultVehicleId: Int? = null,
    /** Record types the user switched off; they are hidden on the vehicle screen. */
    val hidden: Set<RecordType> = emptySet(),
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            server = prefs[URL]?.let { Server(it, prefs[USERNAME].orEmpty(), prefs[PASSWORD].orEmpty()) },
            units = Units.entries.firstOrNull { it.name == prefs[UNITS] } ?: Units.Metric,
            currency = prefs[CURRENCY_CHOICE] ?: prefs[CURRENCY] ?: "$",
            serverCurrency = prefs[CURRENCY] ?: "$",
            // FUEL_PRICE is the single price from before petrol and diesel were split.
            petrolPrice = prefs[PETROL_PRICE] ?: prefs[FUEL_PRICE],
            dieselPrice = prefs[DIESEL_PRICE] ?: prefs[FUEL_PRICE],
            onlineFuelPrices = prefs[ONLINE_PRICES] == true,
            fuelCountry = prefs[FUEL_COUNTRY],
            countryPrices = prefs[COUNTRY_PRICES]
                ?.takeIf { prefs[ONLINE_PRICES] == true }
                ?.let { saved -> runCatching { Json.decodeFromString<CountryFuelPrices>(saved) }.getOrNull() }
                ?.takeIf { it.code == prefs[FUEL_COUNTRY] },
            fuelByTrip = prefs[FUEL_BY_TRIP] == true,
            reminderNotifications = prefs[NOTIFY_REMINDERS] != false,
            notificationsAsked = prefs[NOTIFY_ASKED] == true,
            defaultVehicleId = prefs[DEFAULT_VEHICLE],
            hidden = RecordType.entries.filter { it.name in prefs[HIDDEN].orEmpty() }.toSet(),
        )
    }

    suspend fun connect(server: Server, currency: String) {
        context.dataStore.edit {
            it[URL] = server.url
            it[USERNAME] = server.username
            it[PASSWORD] = server.password
            it[CURRENCY] = currency
        }
    }

    suspend fun disconnect() {
        context.dataStore.edit {
            it.remove(URL)
            it.remove(USERNAME)
            it.remove(PASSWORD)
            it.remove(DEFAULT_VEHICLE) // ids mean nothing on another server
        }
    }

    suspend fun setUnits(units: Units) {
        context.dataStore.edit { it[UNITS] = units.name }
    }

    /** Pass null to go back to the symbol the server uses. */
    suspend fun setCurrency(symbol: String?) {
        context.dataStore.edit {
            if (symbol.isNullOrBlank()) it.remove(CURRENCY_CHOICE) else it[CURRENCY_CHOICE] = symbol.trim()
        }
    }

    /** Pass null to go back to starting from the last fill-up's price. */
    suspend fun setFuelPrice(fuel: FuelType, price: Double?) {
        val key = if (fuel == FuelType.Diesel) DIESEL_PRICE else PETROL_PRICE
        context.dataStore.edit {
            it.remove(FUEL_PRICE)
            if (price == null || price <= 0) it.remove(key) else it[key] = price
        }
    }

    suspend fun setOnlineFuelPrices(enabled: Boolean) {
        context.dataStore.edit { it[ONLINE_PRICES] = enabled }
    }

    suspend fun setFuelCountry(code: String) {
        context.dataStore.edit { it[FUEL_COUNTRY] = code.uppercase() }
    }

    /**
     * Remembers fetched prices (so they are there at once next time, and when offline) and
     * writes them over the petrol and diesel prices in settings, per the app's fuel unit.
     */
    suspend fun saveCountryPrices(prices: CountryFuelPrices, units: Units) {
        context.dataStore.edit {
            it[COUNTRY_PRICES] = Json.encodeToString(prices)
            prices.pricePer(units, FuelType.Gasoline)?.let { price -> it[PETROL_PRICE] = price }
            prices.pricePer(units, FuelType.Diesel)?.let { price -> it[DIESEL_PRICE] = price }
        }
    }

    suspend fun setFuelByTrip(byTrip: Boolean) {
        context.dataStore.edit { it[FUEL_BY_TRIP] = byTrip }
    }

    suspend fun setReminderNotifications(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFY_REMINDERS] = enabled }
    }

    suspend fun setNotificationsAsked() {
        context.dataStore.edit { it[NOTIFY_ASKED] = true }
    }

    /** Pass null to go back to opening the garage. */
    suspend fun setDefaultVehicle(vehicleId: Int?) {
        context.dataStore.edit {
            if (vehicleId == null) it.remove(DEFAULT_VEHICLE) else it[DEFAULT_VEHICLE] = vehicleId
        }
    }

    suspend fun setHidden(type: RecordType, hidden: Boolean) {
        context.dataStore.edit {
            val current = it[HIDDEN].orEmpty()
            it[HIDDEN] = if (hidden) current + type.name else current - type.name
        }
    }

    private companion object {
        val URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val UNITS = stringPreferencesKey("units")
        val CURRENCY = stringPreferencesKey("currency") // the server's
        val CURRENCY_CHOICE = stringPreferencesKey("currency_choice") // the user's, if any
        val FUEL_PRICE = doublePreferencesKey("fuel_price")
        val PETROL_PRICE = doublePreferencesKey("fuel_price_petrol")
        val DIESEL_PRICE = doublePreferencesKey("fuel_price_diesel")
        val ONLINE_PRICES = booleanPreferencesKey("online_fuel_prices")
        val FUEL_COUNTRY = stringPreferencesKey("fuel_country")
        val COUNTRY_PRICES = stringPreferencesKey("country_fuel_prices")
        val FUEL_BY_TRIP = booleanPreferencesKey("fuel_by_trip")
        val NOTIFY_REMINDERS = booleanPreferencesKey("notify_reminders")
        val NOTIFY_ASKED = booleanPreferencesKey("notify_asked")
        val DEFAULT_VEHICLE = intPreferencesKey("default_vehicle")
        val HIDDEN = stringSetPreferencesKey("hidden_record_types")
    }
}
