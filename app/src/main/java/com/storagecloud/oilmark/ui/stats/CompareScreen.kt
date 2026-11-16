package com.storagecloud.oilmark.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.Record
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.Stats
import com.storagecloud.oilmark.data.Totals
import com.storagecloud.oilmark.data.Vehicle
import com.storagecloud.oilmark.ui.common.BackTopBar
import com.storagecloud.oilmark.ui.common.LoadContent
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.Message
import com.storagecloud.oilmark.ui.common.rememberLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import kotlin.math.roundToInt

/** Every vehicle with all its costs and distances, loaded in one go (one call per record type). */
private class CompareData(val vehicles: List<Vehicle>, private val records: Map<RecordType, List<Record>>) {
    fun of(vehicleId: Int): Map<RecordType, List<Record>> =
        records.mapValues { (_, list) -> list.filter { it.vehicleId == vehicleId } }
}

/** The time span the vehicle comparison covers. */
private enum class Span(val title: String) {
    ThisYear("This year"),
    LastYear("Last 12 months"),
    AllTime("All time"),
}

/**
 * Side by side: one vehicle year against year, or all vehicles against each other.
 * [vehicleId] is the vehicle the "Years" tab starts on.
 */
@Composable
fun CompareScreen(vehicleId: Int?, onBack: () -> Unit) {
    val api = LocalApi.current
    val loader = rememberLoader {
        coroutineScope {
            val vehicles = async { api.vehicles().map { it.vehicle } }
            val records = (Stats.spendingTypes + RecordType.Odometer).map { type -> async { type to api.allRecords(type) } }
            CompareData(vehicles.await(), records.awaitAll().toMap())
        }
    }

    Scaffold(topBar = { BackTopBar("Compare", onBack) }) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { data ->
            if (data.vehicles.isEmpty()) {
                Message(Icons.Outlined.DirectionsCar, "No vehicles yet", "Add a vehicle first.")
                return@LoadContent
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                var byVehicle by rememberSaveable { mutableStateOf(false) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false, true).forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == byVehicle,
                            onClick = { byVehicle = option },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(if (option) "Vehicles" else "Years") }
                    }
                }
                if (byVehicle) VehiclesContent(data) else YearsContent(data, vehicleId)
            }
        }
    }
}

@Composable
private fun YearsContent(data: CompareData, startVehicleId: Int?) {
    var chosen by rememberSaveable {
        mutableIntStateOf(data.vehicles.firstOrNull { it.id == startVehicleId }?.id ?: data.vehicles.first().id)
    }
    if (data.vehicles.size > 1) VehiclePicker(data.vehicles, chosen) { chosen = it }
    val vehicle = data.vehicles.first { it.id == chosen }
    val today = remember { LocalDate.now() }
    val years = remember(chosen) {
        val records = data.of(chosen)
        val first = maxOf(Stats.firstYear(records) ?: today.year, today.year - 9)
        (today.year downTo first).map { year ->
            year to Stats.totals(records, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
        }
    }
    Text(
        "Rates (per 100 ${LocalFormats.current.units.distance}, consumption, price) compare fairly even " +
            "with a year that isn't over; totals don't.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    years.forEachIndexed { index, (year, totals) ->
        val previous = years.getOrNull(index + 1)
        MetricsCard(
            title = if (year == today.year) "$year (so far)" else "$year",
            totals = totals,
            electric = vehicle.isElectric,
            previous = previous?.second,
            previousLabel = previous?.first?.toString(),
        )
    }
}

@Composable
private fun VehiclesContent(data: CompareData) {
    var span by rememberSaveable { mutableStateOf(Span.ThisYear) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in Span.entries) {
            FilterChip(selected = option == span, onClick = { span = option }, label = { Text(option.title) })
        }
    }
    val today = remember { LocalDate.now() }
    val all = remember(span) {
        val (start, end) = when (span) {
            Span.ThisYear -> LocalDate.of(today.year, 1, 1) to today.plusDays(1)
            Span.LastYear -> today.minusYears(1).plusDays(1) to today.plusDays(1)
            Span.AllTime -> LocalDate.of(1900, 1, 1) to today.plusDays(1)
        }
        data.vehicles.map { it to Stats.totals(data.of(it.id), start, end) }
    }
    // Only worth pointing out with something to compare against.
    val cheapest = all.filter { it.second.costPer100 != null }.takeIf { it.size > 1 }
        ?.minByOrNull { it.second.costPer100!! }?.first?.id
    for ((vehicle, totals) in all) {
        MetricsCard(
            title = vehicle.name,
            totals = totals,
            electric = vehicle.isElectric,
            note = "Cheapest to run".takeIf { vehicle.id == cheapest },
        )
    }
}

@Composable
private fun VehiclePicker(vehicles: List<Vehicle>, chosen: Int, onChoose: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (vehicle in vehicles) {
            FilterChip(selected = vehicle.id == chosen, onClick = { onChoose(vehicle.id) }, label = { Text(vehicle.name) })
        }
    }
}

/**
 * The key numbers for one year or one vehicle. With [previous], the rates also show how
 * much they changed since then.
 */
@Composable
private fun MetricsCard(
    title: String,
    totals: Totals,
    electric: Boolean,
    previous: Totals? = null,
    previousLabel: String? = null,
    note: String? = null,
) {
    val formats = LocalFormats.current
    val per = "per 100 ${formats.units.distance}"
    fun consumption(t: Totals) = if (t.measuredDistance > 0) {
        formats.consumptionValue(t.measuredDistance, t.measuredFuel, electric)
    } else {
        null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (totals.isEmpty) {
                Text(
                    "Nothing recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Metric("Distance driven", formats.distance(totals.distance))
            Metric("Total spent", formats.money(totals.spent))
            Metric(
                "All costs $per", totals.costPer100?.let(formats::money),
                change(totals.costPer100, previous?.costPer100, previousLabel),
            )
            Metric(
                "Fuel $per", totals.fuelCostPer100?.let(formats::money),
                change(totals.fuelCostPer100, previous?.fuelCostPer100, previousLabel),
            )
            Metric(
                "Consumption", consumption(totals)?.let { formats.economy(it, electric) },
                change(consumption(totals), previous?.let(::consumption), previousLabel),
            )
            Metric(
                "Average fuel price", totals.averageFuelPrice?.let { formats.pricePerUnit(it, electric) },
                change(totals.averageFuelPrice, previous?.averageFuelPrice, previousLabel),
            )
        }
    }
}

/** "+8% vs 2025", or null when there is nothing to compare. */
private fun change(now: Double?, before: Double?, label: String?): String? {
    if (now == null || before == null || before <= 0 || label == null) return null
    val percent = ((now - before) / before * 100).roundToInt()
    return when {
        percent > 0 -> "+$percent% vs $label"
        percent < 0 -> "−${-percent}% vs $label"
        else -> "same as $label"
    }
}

@Composable
private fun Metric(label: String, value: String?, change: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(value ?: "–", style = MaterialTheme.typography.bodyLarge)
            if (change != null) {
                Text(change, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
