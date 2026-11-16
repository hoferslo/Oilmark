package com.storagecloud.oilmark.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.Country
import com.storagecloud.oilmark.data.FuelPriceSource
import com.storagecloud.oilmark.data.FuelType
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.LocalSettings
import com.storagecloud.oilmark.ui.common.LocalSettingsStore
import com.storagecloud.oilmark.ui.common.TextInput
import com.storagecloud.oilmark.ui.common.ToggleInput
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The "get today's fuel price online" part of settings: an on/off switch, the country, and
 * the prices last fetched (with the credit link the data's licence requires).
 */
@Composable
fun OnlineFuelPriceSettings() {
    val settings = LocalSettings.current
    val store = LocalSettingsStore.current
    val formats = LocalFormats.current
    val scope = rememberCoroutineScope()
    var choosingCountry by remember { mutableStateOf(false) }

    ToggleInput(
        "Get today's price online", settings.onlineFuelPrices,
        onChange = { on ->
            scope.launch {
                store.setOnlineFuelPrices(on)
                // First time on: start with the phone's own country.
                if (on && settings.fuelCountry == null) {
                    Locale.getDefault().country.takeIf { it.length == 2 }?.let { store.setFuelCountry(it) }
                }
            }
        },
        icon = Icons.Outlined.Public,
    )
    if (!settings.onlineFuelPrices) return

    val prices = settings.countryPrices
    Card(Modifier.fillMaxWidth()) {
        val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
        ListItem(
            leadingContent = { Icon(Icons.Outlined.Flag, contentDescription = null) },
            overlineContent = { Text("Country") },
            headlineContent = { Text(prices?.name ?: settings.fuelCountry ?: "Choose a country") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = transparent,
            modifier = Modifier.clickable { choosingCountry = true },
        )
        if (prices != null) {
            fun line(label: String, fuel: FuelType) = prices.pricePer(settings.units, fuel)
                ?.let { "$label ${String.format(Locale.getDefault(), "%.3f", it)}" }
            val date = formats.date(
                Instant.ofEpochMilli(prices.fetchedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            )
            ListItem(
                overlineContent = { Text("Average price per ${settings.units.fuel}, in ${prices.currency}") },
                headlineContent = {
                    Text(listOfNotNull(line("Petrol", FuelType.Gasoline), line("Diesel", FuelType.Diesel)).joinToString("  ·  "))
                },
                supportingContent = {
                    Column {
                        Text("Fetched $date")
                        val browser = LocalUriHandler.current
                        Text(
                            "Data: ${FuelPriceSource.CREDIT} (CC BY 4.0)",
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { browser.openUri(FuelPriceSource.CREDIT_URL) },
                        )
                    }
                },
                colors = transparent,
            )
        } else {
            ListItem(
                headlineContent = { Text("No prices yet") },
                supportingContent = { Text("They are fetched when the app opens. Check the country and your connection.") },
                colors = transparent,
            )
        }
    }

    if (choosingCountry) {
        CountryDialog(
            onPick = { country ->
                choosingCountry = false
                scope.launch { store.setFuelCountry(country.code) }
            },
            onDismiss = { choosingCountry = false },
        )
    }
}

/** A searchable list of every country the price service covers; loaded when opened. */
@Composable
private fun CountryDialog(onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var countries by remember { mutableStateOf<List<Country>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            countries = FuelPriceSource.countries()
        } catch (e: ApiException) {
            error = e.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Country") },
        text = {
            Column {
                TextInput("Search", search, { search = it }, icon = Icons.Outlined.Search)
                val all = countries
                when {
                    error != null -> Text(error.orEmpty(), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
                    all == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(all.filter { it.name.contains(search.trim(), ignoreCase = true) }, key = { it.code }) { country ->
                            ListItem(
                                headlineContent = { Text(country.name) },
                                trailingContent = { Text(country.code) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onPick(country) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
