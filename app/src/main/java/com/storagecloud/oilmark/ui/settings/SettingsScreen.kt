package com.storagecloud.oilmark.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Garage
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.FuelType
import com.storagecloud.oilmark.data.Vehicle
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.Units
import com.storagecloud.oilmark.ui.common.BackTopBar
import com.storagecloud.oilmark.ui.common.Load
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalSettings
import com.storagecloud.oilmark.ui.common.LocalSettingsStore
import com.storagecloud.oilmark.ui.common.NumberInput
import com.storagecloud.oilmark.ui.common.TextInput
import com.storagecloud.oilmark.ui.common.VehiclePhoto
import com.storagecloud.oilmark.ui.common.rememberLoader
import com.storagecloud.oilmark.ui.records.icon
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val api = LocalApi.current
    val settings = LocalSettings.current
    val store = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    val version = rememberLoader { api.serverInfo().currentVersion }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    Scaffold(topBar = { BackTopBar("Settings", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading("Show on vehicles")
            Card(Modifier.fillMaxWidth()) {
                for (type in RecordType.entries) {
                    val shown = type !in settings.hidden
                    ListItem(
                        leadingContent = { Icon(type.icon, contentDescription = null) },
                        headlineContent = { Text(type.title) },
                        trailingContent = { Switch(checked = shown, onCheckedChange = null) },
                        colors = transparent,
                        modifier = Modifier.toggleable(
                            value = shown,
                            role = Role.Switch,
                            onValueChange = { show -> scope.launch { store.setHidden(type, hidden = !show) } },
                        ),
                    )
                }
            }
            Note("Switch off what you don't use. Nothing is deleted - it is only hidden in this app.")

            Heading("Notifications")
            ReminderNotificationSetting()

            Heading("Start screen")
            // Looked up by id, so it shows the vehicle's current name and photo.
            val starredId = settings.defaultVehicleId
            val starred by produceState<Vehicle?>(null, starredId) {
                value = try {
                    starredId?.let { api.vehicle(it).vehicle }
                } catch (_: ApiException) {
                    null
                }
            }
            Card(Modifier.fillMaxWidth()) {
                val vehicle = starred
                ListItem(
                    leadingContent = {
                        if (vehicle != null) {
                            VehiclePhoto(
                                vehicle,
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        } else {
                            Icon(
                                if (starredId == null) Icons.Outlined.Garage else Icons.Filled.Star,
                                contentDescription = null,
                            )
                        }
                    },
                    overlineContent = if (starredId != null) ({ Text("Starred vehicle") }) else null,
                    headlineContent = {
                        Text(
                            when {
                                starredId == null -> "Garage"
                                vehicle != null -> vehicle.name
                                else -> "Your starred vehicle"
                            },
                        )
                    },
                    supportingContent = {
                        Text(
                            when {
                                starredId == null -> "Tap the star on a vehicle to open it straight away at start."
                                vehicle?.licensePlate.isNullOrBlank() -> "The app opens straight to it."
                                else -> "${vehicle.licensePlate} · the app opens straight to it."
                            },
                        )
                    },
                    colors = transparent,
                )
            }
            if (settings.defaultVehicleId != null) {
                OutlinedButton(
                    onClick = { scope.launch { store.setDefaultVehicle(null) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open the garage at start instead") }
            }

            Heading("Units")
            Card(Modifier.fillMaxWidth()) {
                for (units in Units.entries) {
                    ListItem(
                        headlineContent = { Text(units.label) },
                        leadingContent = { RadioButton(selected = units == settings.units, onClick = null) },
                        colors = transparent,
                        modifier = Modifier.selectable(
                            selected = units == settings.units,
                            role = Role.RadioButton,
                            onClick = { scope.launch { store.setUnits(units) } },
                        ),
                    )
                }
            }
            Note("Only changes the labels - use the same units you use in LubeLogger.")

            Heading("Fuel price")
            // Rebuilt after each online fetch, so the boxes show the prices it just wrote.
            key(settings.countryPrices?.fetchedAt) {
                NumberInput(
                    "Petrol, per ${settings.units.fuel}", settings.petrolPrice,
                    onChange = { price -> scope.launch { store.setFuelPrice(FuelType.Gasoline, price) } },
                    unit = settings.currency,
                    icon = Icons.Outlined.LocalGasStation,
                )
                NumberInput(
                    "Diesel, per ${settings.units.fuel}", settings.dieselPrice,
                    onChange = { price -> scope.launch { store.setFuelPrice(FuelType.Diesel, price) } },
                    unit = settings.currency,
                    icon = Icons.Outlined.LocalGasStation,
                )
            }
            Note(
                "A new fuel-up starts at the price for the fuel that vehicle runs on. Leave one empty to " +
                    "start from that vehicle's last fill-up instead. Saved on this phone only.",
            )
            OnlineFuelPriceSettings()
            Note(
                if (settings.onlineFuelPrices) {
                    "Each time the app opens it asks openvan.camp for your country's average prices and " +
                        "writes them into the two boxes above, replacing what was there. Only the country is sent."
                } else {
                    "Turn on to have the two prices above filled in for you with your country's current " +
                        "average. Off: the app never contacts any site other than your LubeLogger server."
                },
            )

            Heading("Currency")
            val usingServer = settings.currency == settings.serverCurrency
            val isCustom = !usingServer && settings.currency !in currencySymbols
            // Kept while typing, so clearing the box doesn't make it vanish mid-edit.
            var typingOwn by rememberSaveable { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = usingServer && !typingOwn,
                    onClick = { typingOwn = false; scope.launch { store.setCurrency(null) } },
                    label = { Text("Server (${settings.serverCurrency})") },
                )
                for (symbol in currencySymbols - settings.serverCurrency) {
                    FilterChip(
                        selected = settings.currency == symbol && !typingOwn,
                        onClick = { typingOwn = false; scope.launch { store.setCurrency(symbol) } },
                        label = { Text(symbol) },
                    )
                }
                FilterChip(
                    selected = isCustom || typingOwn,
                    onClick = { typingOwn = true },
                    label = { Text("Other") },
                )
            }
            if (isCustom || typingOwn) {
                var own by rememberSaveable { mutableStateOf(if (isCustom) settings.currency else "") }
                TextInput(
                    "Your symbol", own,
                    onChange = { typed ->
                        own = typed.take(5)
                        scope.launch { store.setCurrency(own) }
                    },
                    icon = Icons.Outlined.Payments,
                )
            }
            Note("Only the symbol changes - amounts stay exactly as they are, nothing is converted.")

            Heading("Android Auto (test)")
            AndroidAutoSetting()
            Note(
                "Adds a screen to Android Auto that shows what your car shares with the phone: model, " +
                    "odometer, speed and fuel level. It stays on until you switch it off. Android Auto only " +
                    "shows it if \"Unknown sources\" is on in Android Auto's developer settings.",
            )

            Heading("Server")
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    overlineContent = { Text("Address") },
                    headlineContent = { Text(settings.server?.url.orEmpty()) },
                    colors = transparent,
                )
                settings.server?.username?.takeIf { it.isNotEmpty() }?.let { username ->
                    ListItem(
                        overlineContent = { Text("Signed in as") },
                        headlineContent = { Text(username) },
                        colors = transparent,
                    )
                }
                ListItem(
                    overlineContent = { Text("LubeLogger version") },
                    headlineContent = {
                        Text(
                            when (val state = version.state) {
                                Load.Loading -> "…"
                                is Load.Failed -> "Not reachable"
                                is Load.Ready -> state.value
                            },
                        )
                    },
                    colors = transparent,
                )
            }
            OutlinedButton(
                onClick = { scope.launch { store.disconnect() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disconnect from this server")
            }
        }
    }
}

private val currencySymbols = listOf("€", "$", "£", "CHF", "kr", "zł", "Kč", "¥", "₹")

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
