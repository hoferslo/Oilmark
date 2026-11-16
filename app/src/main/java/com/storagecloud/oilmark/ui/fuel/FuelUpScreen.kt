package com.storagecloud.oilmark.ui.fuel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.Attachment
import com.storagecloud.oilmark.data.CountryFuelPrices
import com.storagecloud.oilmark.data.Extra
import com.storagecloud.oilmark.data.FuelPriceSource
import com.storagecloud.oilmark.data.FuelType
import com.storagecloud.oilmark.data.Units
import com.storagecloud.oilmark.data.LubeLoggerApi
import com.storagecloud.oilmark.data.Record
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.extra
import com.storagecloud.oilmark.data.withExtra
import com.storagecloud.oilmark.ui.common.ChoiceInput
import com.storagecloud.oilmark.ui.common.LocalSettingsStore
import com.storagecloud.oilmark.ui.common.TextInput
import com.storagecloud.oilmark.ui.common.DateInput
import com.storagecloud.oilmark.ui.common.FormScreen
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.LocalSettings
import com.storagecloud.oilmark.ui.common.NumberInput
import com.storagecloud.oilmark.ui.common.ToggleInput
import com.storagecloud.oilmark.ui.common.marked
import com.storagecloud.oilmark.ui.records.Attachments
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Quick fuel-up entry. Everything starts at the values of the last fill-up, so usually it is
 * just: drag three sliders a little, Save. Tap any big number to type it exactly instead.
 */
@Composable
fun FuelUpScreen(vehicleId: Int, electric: Boolean, onDone: () -> Unit) {
    val api = LocalApi.current
    val formats = LocalFormats.current
    val settings = LocalSettings.current
    val store = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val model = viewModel {
        FuelUpModel(
            api, vehicleId, settings.petrolPrice, settings.dieselPrice, settings.countryPrices, settings.units,
        )
    }
    // Where the phone is: found by itself if location is already allowed, else on request.
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) model.findLocation(context.applicationContext)
    }
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) model.findLocation(context.applicationContext)
    }
    LaunchedEffect(model.done) { if (model.done) onDone() }
    val fuelUnit = formats.fuelUnit(electric)

    FormScreen(
        title = if (electric) "Charge" else "Fuel up",
        onBack = onDone,
        onSave = model::save,
        saving = model.saving,
        error = model.error,
    ) {
        if (model.loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@FormScreen
        }

        // Odometer, or the trip meter (distance since the last fill-up) - whichever the user reads off.
        // Trip only works once there is a fill-up to count from.
        val byTrip = settings.fuelByTrip && model.lastFillOdometer > 0
        if (model.lastFillOdometer > 0) {
            ChoiceInput(
                "Enter", listOf(false, true), byTrip,
                onChange = { trip -> scope.launch { store.setFuelByTrip(trip) } },
                icon = Icons.Outlined.Route,
                name = { if (it) "Trip distance" else "Odometer" },
            )
        }
        if (byTrip) {
            QuickValue(
                label = "Trip since last fill-up",
                required = true,
                icon = Icons.Outlined.Route,
                value = formats.distance(model.trip),
                hint = "Odometer will be ${formats.distance(model.odometer)}",
                slider = model.trip.toFloat(),
                range = 0f..model.drivenMax.toFloat(),
                onSlide = { model.setTrip((it / 10).roundToInt() * 10) },
                onStep = { model.setTrip((model.trip + it).coerceAtLeast(0)) },
                typed = model.trip.toDouble(),
                typedDecimals = false,
                unit = formats.units.distance,
                onType = { model.setTrip(it.toInt()) },
            )
        } else {
            QuickValue(
                label = "Odometer",
                required = true,
                icon = Icons.Outlined.Speed,
                value = formats.distance(model.odometer),
                hint = if (model.lastOdometer > 0) {
                    "+${formats.distance(model.driven)} since ${formats.distance(model.lastOdometer)}"
                } else {
                    null
                },
                slider = model.driven.toFloat(),
                range = 0f..model.drivenMax.toFloat(),
                onSlide = { model.driven = (it / 10).roundToInt() * 10 },
                onStep = { model.driven = (model.driven + it).coerceAtLeast(0) },
                typed = model.odometer.toDouble(),
                typedDecimals = false,
                unit = formats.units.distance,
                onType = model::typeOdometer,
            )
        }
        QuickValue(
            label = "Amount",
            required = true,
            icon = Icons.Outlined.LocalGasStation,
            value = formats.fuel(model.amount, electric),
            slider = model.amount.toFloat(),
            range = 0f..model.amountMax.toFloat(),
            onSlide = { model.amount = (it * 2).roundToInt() / 2.0 },
            onStep = { model.amount = ((model.amount * 10).roundToInt() + it).coerceAtLeast(0) / 10.0 },
            typed = model.amount,
            unit = fuelUnit,
            onType = { model.amount = it },
        )
        QuickValue(
            label = "Price per $fuelUnit",
            icon = Icons.Outlined.Payments,
            hint = model.priceHint,
            value = formats.currency + String.format(Locale.getDefault(), "%.3f", model.price),
            slider = model.price.toFloat(),
            range = model.priceRange,
            onSlide = { model.price = (it * 100).roundToInt() / 100.0 },
            onStep = { model.price = ((model.price * 1000).roundToInt() + it).coerceAtLeast(0) / 1000.0 },
            typed = model.price,
            unit = formats.currency,
            onType = { model.price = it },
        )

        // The total follows amount x price; tap it to type what the receipt says instead.
        var typingTotal by remember { mutableStateOf(false) }
        Card(
            onClick = { typingTotal = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                @Suppress("DEPRECATION") // the mirrored variant only matters for right-to-left languages
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Total", style = MaterialTheme.typography.labelLarge)
                    Text("Tap to type the receipt total", style = MaterialTheme.typography.bodySmall)
                }
                Text(formats.money(model.total), style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (typingTotal) {
            NumberDialog("Total", model.total, true, formats.currency, { model.typeTotal(it) }, { typingTotal = false })
        }

        ToggleInput("Filled to full", model.full, { model.full = it }, icon = Icons.Outlined.Opacity)
        ToggleInput(
            "Missed a fuel-up before this", model.missed, { model.missed = it },
            icon = Icons.Outlined.ErrorOutline,
        )
        TextInput(
            "Station", model.station, { model.station = it },
            icon = Icons.Outlined.Storefront,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        // The stations used most recently, one tap away.
        val suggestions = model.recentStations.filterNot { it.equals(model.station.trim(), ignoreCase = true) }
        if (suggestions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in suggestions) {
                    SuggestionChip(onClick = { model.station = name }, label = { Text(name) })
                }
            }
        }
        LocationRow(
            spot = model.spot,
            finding = model.findingLocation,
            onFind = {
                if (hasLocationPermission(context)) {
                    model.findLocation(context.applicationContext)
                } else {
                    askLocation.launch(LOCATION_PERMISSIONS)
                }
            },
            onClear = { model.spot = null },
        )
        DateInput("Date", model.date, { model.date = it ?: model.date })
        Attachments(model.files) { model.files = it }
    }
}

/** Where the fill-up happened: the coordinates with a link to the map, or a button to add them. */
@Composable
private fun LocationRow(spot: Spot?, finding: Boolean, onFind: () -> Unit, onClear: () -> Unit) {
    val browser = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Place, contentDescription = null,
            Modifier.padding(start = 12.dp, end = 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            finding -> {
                Text("Finding location…", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            spot != null -> {
                Text(
                    String.format(Locale.getDefault(), "%.5f, %.5f", spot.latitude, spot.longitude),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = { browser.openUri("geo:$spot?q=$spot") }) { Text("Map") }
                TextButton(onClick = onClear) { Text("Remove") }
            }
            else -> {
                Text("No location", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onFind) { Text("Add location") }
            }
        }
    }
}

/** A labelled big number with a slider for coarse changes and -/+ buttons for fine ones. */
@Composable
private fun QuickValue(
    label: String,
    icon: ImageVector,
    value: String,
    slider: Float,
    range: ClosedFloatingPointRange<Float>,
    onSlide: (Float) -> Unit,
    /** Called with -1 or +1 for one fine step. */
    onStep: (Int) -> Unit,
    typed: Double,
    unit: String,
    onType: (Double) -> Unit,
    hint: String? = null,
    typedDecimals: Boolean = true,
    required: Boolean = false,
) {
    var typing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { typing = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            icon, contentDescription = null,
                            Modifier.padding(end = 6.dp).size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            label.marked(required),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(value, style = MaterialTheme.typography.headlineMedium)
                    if (hint != null) {
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { onStep(-1) }) { Icon(Icons.Filled.Remove, contentDescription = "Less") }
                    FilledTonalIconButton(onClick = { onStep(1) }) { Icon(Icons.Filled.Add, contentDescription = "More") }
                }
            }
            Slider(
                value = slider.coerceIn(range),
                onValueChange = onSlide,
                valueRange = range,
            )
        }
    }
    if (typing) {
        NumberDialog(label, typed, typedDecimals, unit, onType, onDismiss = { typing = false })
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: Double,
    decimals: Boolean,
    unit: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf<Double?>(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NumberInput(title, value.takeIf { initial != 0.0 }, { value = it }, decimals = decimals, unit = unit) },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

class FuelUpModel(
    private val api: LubeLoggerApi,
    private val vehicleId: Int,
    /** The price per unit from settings, if the user set one; otherwise the last fill-up's is used. */
    private val petrolPrice: Double? = null,
    private val dieselPrice: Double? = null,
    /** The prices last fetched online, only used to credit the source when the price came from there. */
    private val countryPrices: CountryFuelPrices? = null,
    private val units: Units = Units.Metric,
) : ViewModel() {
    /** Says where the starting price came from, when it came from the internet. */
    var priceHint: String? by mutableStateOf(null)
        private set

    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var done by mutableStateOf(false)
        private set

    // ---- the form ----
    var date by mutableStateOf(LocalDate.now().toString())
    /** Odometer at the previous reading; the odometer slider adds [driven] on top of it. */
    var lastOdometer by mutableStateOf(0)
        private set
    var driven by mutableStateOf(0)
    private var amountState by mutableStateOf(0.0)
    private var priceState by mutableStateOf(0.0)
    var amount: Double
        get() = amountState
        set(value) { amountState = value; typedTotal = null }
    var price: Double
        get() = priceState
        set(value) { priceState = value; typedTotal = null }
    var full by mutableStateOf(true)
    var missed by mutableStateOf(false)
    var files: List<Attachment> by mutableStateOf(emptyList())
    private var typedTotal: Double? by mutableStateOf(null)
    var station by mutableStateOf("")
    var spot: Spot? by mutableStateOf(null)
    var findingLocation by mutableStateOf(false)
        private set

    /** Odometer at the last fill-up: the trip meter counts from here. 0 when there is none yet. */
    var lastFillOdometer by mutableStateOf(0)
        private set
    /** Station names used before, most recent first. */
    var recentStations: List<String> by mutableStateOf(emptyList())
        private set
    /** Where earlier fill-ups happened, to recognise the station when standing at it again. */
    private var knownStations: List<Pair<Spot, String>> = emptyList()

    /** Distance since the last fill-up. */
    val trip: Int get() = (odometer - lastFillOdometer).coerceAtLeast(0)

    fun setTrip(distance: Int) = typeOdometer((lastFillOdometer + distance).toDouble())

    /** Looks up where the phone is; also fills in the station if a past fill-up was right here. */
    fun findLocation(context: android.content.Context) {
        if (findingLocation) return
        findingLocation = true
        viewModelScope.launch {
            val here = currentSpot(context)
            if (here != null) {
                spot = here
                if (station.isBlank()) {
                    station = knownStations
                        .map { (place, name) -> place.metresTo(here) to name }
                        .filter { it.first < 250 }
                        .minByOrNull { it.first }?.second.orEmpty()
                }
            }
            findingLocation = false
        }
    }

    // ---- slider ranges, sized from the last fill-up ----
    var drivenMax by mutableStateOf(1500)
        private set
    var amountMax by mutableStateOf(80.0)
        private set
    var priceRange by mutableStateOf(0f..3f)
        private set

    val odometer: Int get() = lastOdometer + driven
    val total: Double get() = typedTotal ?: ((amount * price * 100).roundToInt() / 100.0)

    /** Sets the price and centres its slider on it. */
    private fun startPriceAt(value: Double) {
        price = value
        priceRange = (value - 0.5).coerceAtLeast(0.0).toFloat()..(value + 0.5).toFloat()
    }

    init {
        viewModelScope.launch {
            try {
                val info = api.vehicle(vehicleId)
                lastOdometer = info.lastReportedOdometer
                // The price from settings for the fuel this vehicle runs on (none for electric).
                val fuel = info.vehicle.fuelType
                val saved = when (fuel) {
                    FuelType.Gasoline -> petrolPrice
                    FuelType.Diesel -> dieselPrice
                    FuelType.Electric -> null
                }
                if (saved != null) {
                    startPriceAt(saved)
                    // If it is the online average, credit the source (required by the data's licence).
                    if (countryPrices != null && countryPrices.pricePer(units, fuel) == saved) {
                        priceHint = "Average in ${countryPrices.name} · data: ${FuelPriceSource.CREDIT}"
                    }
                }
                val fills = api.records(RecordType.Fuel, vehicleId).sortedByDescending { it.odometer ?: 0 }
                val last = fills.firstOrNull()
                lastFillOdometer = last?.odometer ?: 0
                val byDate = fills.sortedByDescending { it.date }
                recentStations = byDate.mapNotNull { it.extra(Extra.STATION) }.distinctBy { it.lowercase() }.take(6)
                knownStations = byDate.mapNotNull { fill ->
                    val place = Spot.parse(fill.extra(Extra.LOCATION))
                    val name = fill.extra(Extra.STATION)
                    if (place != null && name != null) place to name else null
                }
                val lastAmount = last?.fuelConsumed ?: 0.0
                if (last != null && lastAmount > 0) {
                    amount = lastAmount
                    amountMax = maxOf(80.0, lastAmount * 1.5)
                    if (saved == null) startPriceAt(((last.cost ?: 0.0) / lastAmount * 1000).roundToInt() / 1000.0)
                }
                // Start the odometer where it would be after a trip like the one before.
                val trip = (last?.odometer ?: 0) - (fills.getOrNull(1)?.odometer ?: 0)
                if (fills.size >= 2 && trip in 1..3000) {
                    driven = (trip - (lastOdometer - (last?.odometer ?: lastOdometer))).coerceAtLeast(0)
                    drivenMax = maxOf(1500, trip * 2)
                }
            } catch (e: ApiException) {
                // Not fatal: the form still works, it just starts empty.
                error = e.message
            }
            loading = false
        }
    }

    /** The user typed the odometer outright. */
    fun typeOdometer(value: Double) {
        val typed = value.toInt()
        if (typed >= lastOdometer) {
            driven = typed - lastOdometer
        } else {
            lastOdometer = typed
            driven = 0
        }
        drivenMax = maxOf(drivenMax, driven)
    }

    /** The user typed the receipt total; the price per unit follows from it. */
    fun typeTotal(value: Double) {
        if (amount > 0) price = (value / amount * 1000).roundToInt() / 1000.0
        typedTotal = value
    }

    fun save() {
        if (saving) return
        if (odometer <= 0 || amount <= 0) {
            error = "Set the odometer and the amount first"
            return
        }
        saving = true
        error = null
        viewModelScope.launch {
            try {
                api.addRecord(
                    RecordType.Fuel, vehicleId,
                    Record(
                        date = date, odometer = odometer, fuelConsumed = amount, cost = total,
                        isFillToFull = full, missedFuelUp = missed, files = files,
                    )
                        .withExtra(Extra.STATION, station)
                        .withExtra(Extra.LOCATION, spot?.toString()),
                )
                done = true
            } catch (e: ApiException) {
                error = e.message
            }
            saving = false
        }
    }
}
