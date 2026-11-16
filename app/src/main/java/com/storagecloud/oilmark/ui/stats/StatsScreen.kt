package com.storagecloud.oilmark.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.Bucket
import com.storagecloud.oilmark.data.Period
import com.storagecloud.oilmark.data.Record
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.Stats
import com.storagecloud.oilmark.data.Tank
import com.storagecloud.oilmark.ui.common.BackTopBar
import com.storagecloud.oilmark.ui.common.LoadContent
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.rememberLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.util.Locale

/** Which analytics the stats screen shows. */
enum class StatsKind(val title: String) {
    Distance("Distance driven"),
    Spending("Spending"),
    Fuel("Fuel analytics"),
}

/** Everything the stats screen needs from the server. */
private class StatsData(val records: Map<RecordType, List<Record>>, val electric: Boolean)

/** Charts and summary numbers for one vehicle: distance, spending or fuel. */
@Composable
fun StatsScreen(vehicleId: Int, kind: StatsKind, onBack: () -> Unit) {
    val api = LocalApi.current
    val types = when (kind) {
        StatsKind.Distance -> Stats.odometerTypes
        StatsKind.Spending -> Stats.spendingTypes
        StatsKind.Fuel -> listOf(RecordType.Fuel)
    }
    val loader = rememberLoader {
        // Everything at once, so the screen takes as long as the slowest call, not the sum.
        coroutineScope {
            val vehicle = async { api.vehicle(vehicleId).vehicle }
            val records = types.map { type -> async { type to api.records(type, vehicleId) } }
            StatsData(records.awaitAll().toMap(), vehicle.await().isElectric)
        }
    }

    Scaffold(topBar = { BackTopBar(kind.title, onBack) }) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { data ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (kind) {
                    StatsKind.Distance -> DistanceContent(data.records.values.flatten())
                    StatsKind.Spending -> SpendingContent(data.records)
                    StatsKind.Fuel -> FuelContent(data.records[RecordType.Fuel].orEmpty(), data.electric)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- distance

@Composable
private fun DistanceContent(records: List<Record>) {
    val formats = LocalFormats.current
    val today = remember { LocalDate.now() }
    val stats = remember(records) { Stats.distanceStats(records, today) }
    var period by rememberSaveable { mutableStateOf(Period.Months) }
    val buckets = remember(records, period) { Stats.distance(records, period, today) }
    // The chart can show either how far the car went per period (bars) or where the odometer stood (line).
    var showReading by rememberSaveable { mutableStateOf(false) }
    val readings = remember(records, period) { Stats.odometer(records, period, today) }
    val charted = if (showReading) readings else buckets
    fun distance(value: Double?) = value?.let { formats.distance(it.toInt()) } ?: "–"

    PeriodToggle(period) { period = it }
    Headline(
        label = if (period == Period.Months) "Last 12 months" else "${buckets.first().label} – ${buckets.last().label}",
        value = distance(buckets.sumOf { it.total }),
        note = stats.firstDate?.let {
            "Readings since ${formats.date(it.toString())}. Between two readings the distance is spread evenly."
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !showReading, onClick = { showReading = false }, label = { Text("Distance driven") })
        FilterChip(selected = showReading, onClick = { showReading = true }, label = { Text("Odometer reading") })
    }
    ChartCard(
        charted, text = { distance(it) }, line = showReading,
        axis = { formats.number(it) }, unit = formats.units.distance,
    )

    val busiest = buckets.maxByOrNull { it.total }?.takeIf { it.total > 0 }
    Tiles(
        "Average per day" to distance(stats.perDay),
        "Average per month" to distance(stats.perMonth),
        "Average per year" to distance(stats.perYear),
        "Last 30 days" to distance(stats.last30Days.toDouble()),
        "This year so far" to distance(stats.thisYear.toDouble()),
        "On pace for this year" to distance(stats.projectedThisYear?.toDouble()),
        "Total on record" to distance(stats.total.toDouble()),
        (if (period == Period.Months) "Busiest month" else "Busiest year") to
            (busiest?.let { "${it.name}\n${distance(it.total)}" } ?: "–"),
    )
    Table(charted, text = { distance(it) })
}

// ---------------------------------------------------------------- spending

@Composable
private fun SpendingContent(records: Map<RecordType, List<Record>>) {
    val formats = LocalFormats.current
    var period by rememberSaveable { mutableStateOf(Period.Months) }
    val buckets = remember(records, period) { Stats.spending(records, period, LocalDate.now()) }
    val total = buckets.sumOf { it.total }
    val active = buckets.count { it.total > 0 }.coerceAtLeast(1)

    PeriodToggle(period) { period = it }
    Headline(
        label = if (period == Period.Months) "Last 12 months" else "${buckets.first().label} – ${buckets.last().label}",
        value = formats.money(total),
        note = "${formats.money(total / active)} per ${if (period == Period.Months) "month" else "year"} with spending",
    )
    ChartCard(
        buckets, text = formats::money, series = Stats.spendingTypes.map { it.title },
        axis = { formats.number(it) }, unit = formats.currency,
    )

    // Where the money went over the whole range.
    Tiles(
        *Stats.spendingTypes.mapIndexed { index, type ->
            val sum = buckets.sumOf { it.values[index] }
            type.title to "${formats.money(sum)}\n${if (total > 0) (sum / total * 100).toInt() else 0}% of the total"
        }.toTypedArray(),
    )
    Table(buckets, text = formats::money)
}

// ---------------------------------------------------------------- fuel

/** What the fuel chart can plot. */
private enum class FuelMetric(val title: String) {
    Consumption("Consumption"),
    Cost("Cost"),
    Amount("Amount"),
    Price("Price"),
}

@Composable
private fun FuelContent(records: List<Record>, electric: Boolean) {
    val formats = LocalFormats.current
    val stats = remember(records) { Stats.fuel(records) }
    if (stats.fillUps == 0) {
        Headline("No fuel-ups yet", "–", "Add a few fuel-ups and the numbers appear here.")
        return
    }
    fun consumption(tank: Tank?) = tank?.let { formats.consumption(it.distance, it.fuel, electric) } ?: "–"
    fun price(value: Double?) = value?.let { formats.pricePerUnit(it, electric) } ?: "–"

    Headline(
        label = "Average consumption",
        value = if (stats.tanks.isEmpty()) "–" else formats.consumption(stats.measuredDistance, stats.measuredFuel, electric),
        note = if (stats.tanks.isEmpty()) {
            "Needs two fill-ups to full in a row, so the fuel used in between is known."
        } else {
            "Measured over ${formats.distance(stats.measuredDistance)}, full tank to full tank"
        },
    )

    Tiles(
        "Last tank" to consumption(stats.lastTank),
        "Fuel cost per distance" to formats.costPer100(stats.measuredCost, stats.measuredDistance),
        "Best tank" to consumption(stats.bestTank),
        "Worst tank" to consumption(stats.worstTank),
        "Distance per tank" to (stats.averageTankDistance?.let(formats::distance) ?: "–"),
        "Days between fill-ups" to (stats.daysBetweenFillUps?.let { formats.number(it) } ?: "–"),
    )

    // Chart: pick what to plot, by month or year.
    var period by rememberSaveable { mutableStateOf(Period.Months) }
    var metric by rememberSaveable { mutableStateOf(FuelMetric.Consumption) }
    val periods = remember(records, period) { Stats.fuelPeriods(records, period, LocalDate.now()) }
    val buckets = periods.map { p ->
        val value = when (metric) {
            FuelMetric.Consumption -> formats.consumptionValue(p.measuredDistance, p.measuredFuel, electric)
            FuelMetric.Cost -> p.cost
            FuelMetric.Amount -> p.fuel
            FuelMetric.Price -> if (p.fuel > 0) p.cost / p.fuel else 0.0
        }
        Bucket(p.label, p.name, listOf(value))
    }
    fun text(value: Double) = when (metric) {
        FuelMetric.Consumption -> formats.economy(value, electric)
        FuelMetric.Cost -> formats.money(value)
        FuelMetric.Amount -> formats.fuel(value, electric)
        FuelMetric.Price -> formats.pricePerUnit(value, electric)
    }
    PeriodToggle(period) { period = it }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in FuelMetric.entries) {
            FilterChip(selected = option == metric, onClick = { metric = option }, label = { Text(option.title) })
        }
    }
    ChartCard(
        buckets, text = ::text, line = metric == FuelMetric.Consumption || metric == FuelMetric.Price,
        // The scale shows bare numbers; what they are in is written once above it.
        axis = { if (metric == FuelMetric.Price) String.format(Locale.getDefault(), "%.3f", it) else formats.number(it) },
        unit = when (metric) {
            FuelMetric.Consumption -> formats.economyUnit(electric)
            FuelMetric.Price -> "${formats.currency} per ${formats.fuelUnit(electric)}"
            FuelMetric.Cost -> formats.currency
            FuelMetric.Amount -> formats.fuelUnit(electric)
        },
    )

    Tiles(
        "Fill-ups" to stats.fillUps.toString(),
        "Total fuel" to formats.fuel(stats.totalFuel, electric),
        "Total fuel cost" to formats.money(stats.totalCost),
        "Fuel cost per month" to (stats.costPerMonth?.let(formats::money) ?: "–"),
        "Average fill-up" to (stats.averageFill?.let { formats.fuel(it, electric) } ?: "–"),
        "Average cost per fill-up" to (stats.averageFillCost?.let(formats::money) ?: "–"),
        "Average price" to price(stats.averagePrice),
        "Last price" to price(stats.lastPrice),
        "Lowest price" to price(stats.lowestPrice),
        "Highest price" to price(stats.highestPrice),
    )
    Table(buckets, text = ::text)
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun PeriodToggle(period: Period, onChange: (Period) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == period,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, Period.entries.size),
            ) { Text(option.name) }
        }
    }
}

/** The one big number of the screen. */
@Composable
private fun Headline(label: String, value: String, note: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small "label + number" cards, two per row. A value may have a second line after a line break. */
@Composable
private fun Tiles(vararg tiles: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in tiles.toList().chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, value) in row) {
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(value.substringBefore('\n'), style = MaterialTheme.typography.titleMedium)
                            if ('\n' in value) {
                                Text(
                                    value.substringAfter('\n'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The bar chart with the selected bar's numbers above it and, for several series, a legend below. */
@Composable
private fun ChartCard(
    buckets: List<Bucket>,
    text: (Double) -> String,
    series: List<String> = emptyList(),
    /** Draw a line instead of bars: for levels that move over time rather than amounts that add up. */
    line: Boolean = false,
    /** Short labels for the scale down the left of the chart; the unit is given in [unit]. */
    axis: (Double) -> String = text,
    /** What the numbers on the scale are in, e.g. "km" - shown above them. */
    unit: String? = null,
) {
    // Starts on the newest bar; starts over when the bars change (months <-> years).
    var selected by remember(buckets.size) { mutableIntStateOf(buckets.lastIndex) }
    val colors = seriesColors()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val bucket = buckets[selected.coerceIn(buckets.indices)]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bucket.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(if (bucket.total > 0) text(bucket.total) else "–", style = MaterialTheme.typography.titleMedium)
            }
            // What the numbers down the left side are in.
            if (unit != null) {
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (line) {
                LineChart(buckets, colors.first(), selected, onSelect = { selected = it }, axis = axis)
            } else {
                BarChart(buckets, colors, selected, onSelect = { selected = it }, axis = axis)
            }

            // Legend, doubling as the breakdown of the selected bar.
            if (series.isNotEmpty()) {
                series.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Swatch(colors[index])
                        Text(name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text(bucket.values[index]),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    if (line) "Tap a point to see its number." else "Tap a bar to see its number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The chart's numbers as a list, newest first. */
@Composable
private fun Table(buckets: List<Bucket>, text: (Double) -> String) {
    Card(Modifier.fillMaxWidth()) {
        buckets.asReversed().forEachIndexed { index, bucket ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(bucket.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (bucket.total > 0) text(bucket.total) else "–",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bucket.total > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
}
