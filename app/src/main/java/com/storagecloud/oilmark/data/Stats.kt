package com.storagecloud.oilmark.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How charts group time. */
enum class Period { Months, Years }

/** One bar of a chart: a month or a year, with one value per series (a single value if there is one series). */
data class Bucket(
    /** Short, for under the bar: "Nov" or "2026". */
    val label: String,
    /** Long, for the detail line: "November 2026" or "2026". */
    val name: String,
    val values: List<Double>,
) {
    val total: Double get() = values.sum()
}

/**
 * One stretch from a full tank to the next full tank - the only honest way to measure
 * consumption: [fuel] is everything put in to cover [distance].
 */
data class Tank(val date: LocalDate, val distance: Int, val fuel: Double, val cost: Double)

/** Everything the fuel analytics screen shows, as plain numbers (formatting is the UI's job). */
data class FuelStats(
    val fillUps: Int = 0,
    val totalFuel: Double = 0.0,
    val totalCost: Double = 0.0,
    /** Full-tank to full-tank stretches, oldest first. Empty until there are two full fill-ups. */
    val tanks: List<Tank> = emptyList(),
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
    /** Price per unit of fuel at the most recent / cheapest / dearest fill-up. */
    val lastPrice: Double? = null,
    val lowestPrice: Double? = null,
    val highestPrice: Double? = null,
) {
    /** Distance, fuel and cost that consumption figures are based on. */
    val measuredDistance: Int get() = tanks.sumOf { it.distance }
    val measuredFuel: Double get() = tanks.sumOf { it.fuel }
    val measuredCost: Double get() = tanks.sumOf { it.cost }

    val averagePrice: Double? get() = if (totalFuel > 0) totalCost / totalFuel else null
    val averageFill: Double? get() = if (fillUps > 0) totalFuel / fillUps else null
    val averageFillCost: Double? get() = if (fillUps > 0) totalCost / fillUps else null
    val averageTankDistance: Int? get() = if (tanks.isNotEmpty()) measuredDistance / tanks.size else null

    /** The tank that used the least / most fuel per distance. */
    val bestTank: Tank? get() = tanks.minByOrNull { it.fuel / it.distance }
    val worstTank: Tank? get() = tanks.maxByOrNull { it.fuel / it.distance }
    val lastTank: Tank? get() = tanks.lastOrNull()

    /** Days from the first to the last fill-up. */
    val days: Long get() = if (firstDate != null && lastDate != null) ChronoUnit.DAYS.between(firstDate, lastDate) else 0
    val daysBetweenFillUps: Double? get() = if (fillUps > 1 && days > 0) days.toDouble() / (fillUps - 1) else null
    val costPerMonth: Double? get() = if (days >= 14) totalCost / days * DAYS_PER_MONTH else null
}

/** Fuel numbers for one month or year, for the fuel charts. */
data class FuelPeriod(
    val label: String,
    val name: String,
    val fuel: Double,
    val cost: Double,
    /** From the tanks that ended in this period: what consumption is computed from. */
    val measuredDistance: Int,
    val measuredFuel: Double,
)

/** Everything the distance screen shows besides the chart. */
data class DistanceStats(
    val firstDate: LocalDate? = null,
    /** From the lowest to the highest odometer reading on record. */
    val total: Int = 0,
    val perDay: Double? = null,
    val last30Days: Int = 0,
    val thisYear: Int = 0,
    /** Where this year ends up if the rest of it goes like the year so far. */
    val projectedThisYear: Int? = null,
) {
    val perMonth: Double? get() = perDay?.times(DAYS_PER_MONTH)
    val perYear: Double? get() = perDay?.times(365.25)
}

private const val DAYS_PER_MONTH = 30.44

/** Turns lists of records into chart data and summary numbers. Pure functions, no server calls. */
object Stats {

    /** The record types that cost money, in the order they are stacked and coloured in charts. */
    val spendingTypes = listOf(
        RecordType.Fuel, RecordType.Service, RecordType.Repair, RecordType.Upgrade, RecordType.Tax,
    )

    /** The record types that carry an odometer reading. */
    val odometerTypes = listOf(
        RecordType.Odometer, RecordType.Fuel, RecordType.Service, RecordType.Repair, RecordType.Upgrade,
    )

    // ---- spending ----

    /** Money spent per period; each bucket has one value per entry of [spendingTypes]. */
    fun spending(records: Map<RecordType, List<Record>>, period: Period, today: LocalDate): List<Bucket> {
        val dated = spendingTypes.map { type ->
            records[type].orEmpty().mapNotNull { record ->
                Formats.parseDate(record.date)?.let { it to (record.cost ?: 0.0) }
            }
        }
        return spans(period, today, earliest = dated.flatten().minOfOrNull { it.first }).map { span ->
            Bucket(span.label, span.name, dated.map { series -> series.filter { it.first in span }.sumOf { it.second } })
        }
    }

    // ---- distance ----

    /** Distance driven per period, worked out from every odometer reading in [records]. */
    fun distance(records: List<Record>, period: Period, today: LocalDate): List<Bucket> {
        val readings = readings(records)
        return spans(period, today, earliest = readings.firstOrNull()?.first).map { span ->
            Bucket(span.label, span.name, listOf(driven(readings, span.start, span.end).toDouble()))
        }
    }

    /**
     * The odometer reading at the end of each period (for the current period: the latest reading).
     * Periods before the first reading have no value. Ends that fall between two readings are
     * estimated in a straight line, like [distance].
     */
    fun odometer(records: List<Record>, period: Period, today: LocalDate): List<Bucket> {
        val readings = readings(records)
        return spans(period, today, earliest = readings.firstOrNull()?.first).map { span ->
            val known = readings.isNotEmpty() && span.end > readings.first().first
            Bucket(span.label, span.name, listOf(if (known) odometerAt(readings, span.end).roundToInt().toDouble() else 0.0))
        }
    }

    fun distanceStats(records: List<Record>, today: LocalDate): DistanceStats {
        val readings = readings(records)
        val first = readings.firstOrNull() ?: return DistanceStats()
        val total = readings.maxOf { it.second } - readings.minOf { it.second }
        val days = ChronoUnit.DAYS.between(first.first, readings.last().first)
        val yearStart = LocalDate.of(today.year, 1, 1)
        val thisYear = driven(readings, yearStart, today.plusDays(1))
        // Days this year that we actually have readings for (the first reading may be mid-year).
        val daysCovered = ChronoUnit.DAYS.between(maxOf(yearStart, first.first), today)
        return DistanceStats(
            firstDate = first.first,
            total = total,
            perDay = if (days >= 7) total.toDouble() / days else null,
            last30Days = driven(readings, today.minusDays(30), today.plusDays(1)),
            thisYear = thisYear,
            projectedThisYear = if (daysCovered >= 30) {
                val remaining = ChronoUnit.DAYS.between(today, yearStart.plusYears(1))
                (thisYear + thisYear.toDouble() / daysCovered * remaining).toInt()
            } else {
                null
            },
        )
    }

    /** (date, odometer) of every record that has both, oldest first and never going backwards. */
    private fun readings(records: List<Record>): List<Pair<LocalDate, Int>> {
        var highest = 0
        return records
            .mapNotNull { record ->
                val date = Formats.parseDate(record.date)
                val odometer = record.odometer?.takeIf { it > 0 }
                if (date != null && odometer != null) date to odometer else null
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            // A typo'd lower reading must not show up as negative distance.
            .map { (date, odometer) -> highest = maxOf(highest, odometer); date to highest }
    }

    /**
     * Distance covered from [start] to [end]. Between two readings the distance is spread
     * evenly over the days, so a month without any reading still gets its fair share.
     */
    private fun driven(readings: List<Pair<LocalDate, Int>>, start: LocalDate, end: LocalDate): Int =
        if (readings.isEmpty()) 0 else (odometerAt(readings, end) - odometerAt(readings, start)).roundToInt()

    /** The odometer on [date], estimated in a straight line between the readings around it. */
    private fun odometerAt(readings: List<Pair<LocalDate, Int>>, date: LocalDate): Double {
        if (date <= readings.first().first) return readings.first().second.toDouble()
        if (date >= readings.last().first) return readings.last().second.toDouble()
        val next = readings.indexOfFirst { it.first >= date }
        val (fromDate, from) = readings[next - 1]
        val (toDate, to) = readings[next]
        val share = ChronoUnit.DAYS.between(fromDate, date).toDouble() / ChronoUnit.DAYS.between(fromDate, toDate)
        return from + (to - from) * share
    }

    // ---- fuel ----

    fun fuel(records: List<Record>): FuelStats {
        val fills = fills(records)
        if (fills.isEmpty()) return FuelStats()
        val prices = fills.filter { it.fuel > 0 && it.cost > 0 }.map { it.cost / it.fuel }
        val byDate = fills.sortedBy { it.date }
        return FuelStats(
            fillUps = fills.size,
            totalFuel = fills.sumOf { it.fuel },
            totalCost = fills.sumOf { it.cost },
            tanks = tanks(fills),
            firstDate = byDate.first().date,
            lastDate = byDate.last().date,
            lastPrice = byDate.lastOrNull { it.fuel > 0 && it.cost > 0 }?.let { it.cost / it.fuel },
            lowestPrice = prices.minOrNull(),
            highestPrice = prices.maxOrNull(),
        )
    }

    /** Fuel bought and consumption per period. */
    fun fuelPeriods(records: List<Record>, period: Period, today: LocalDate): List<FuelPeriod> {
        val fills = fills(records)
        val tanks = tanks(fills)
        return spans(period, today, earliest = fills.minOfOrNull { it.date }).map { span ->
            val bought = fills.filter { it.date in span }
            val measured = tanks.filter { it.date in span }
            FuelPeriod(
                label = span.label,
                name = span.name,
                fuel = bought.sumOf { it.fuel },
                cost = bought.sumOf { it.cost },
                measuredDistance = measured.sumOf { it.distance },
                measuredFuel = measured.sumOf { it.fuel },
            )
        }
    }

    private class Fill(val date: LocalDate, val odometer: Int, val fuel: Double, val cost: Double, val full: Boolean, val missed: Boolean)

    /** Usable fill-ups, in the order they were driven (by odometer). */
    private fun fills(records: List<Record>): List<Fill> = records
        .mapNotNull { record ->
            val date = Formats.parseDate(record.date) ?: return@mapNotNull null
            val fuel = record.fuelConsumed ?: 0.0
            if (fuel <= 0) return@mapNotNull null
            Fill(date, record.odometer ?: 0, fuel, record.cost ?: 0.0, record.isFillToFull == true, record.missedFuelUp == true)
        }
        .sortedWith(compareBy<Fill> { it.odometer }.thenBy { it.date })

    /**
     * Full-tank to full-tank stretches. Partial fill-ups in between are added to the fuel of
     * their stretch; a "missed fuel-up" breaks the chain, because fuel went in that we don't know about.
     */
    private fun tanks(fills: List<Fill>): List<Tank> {
        val tanks = mutableListOf<Tank>()
        var fullAt: Int? = null // odometer at the last full tank
        var fuel = 0.0
        var cost = 0.0
        for (fill in fills) {
            if (fullAt != null && !fill.missed) {
                fuel += fill.fuel
                cost += fill.cost
                val distance = fill.odometer - fullAt
                if (fill.full && distance > 0) tanks += Tank(fill.date, distance, fuel, cost)
            }
            if (fill.full || fill.missed) {
                fullAt = fill.odometer.takeIf { fill.full && it > 0 }
                fuel = 0.0
                cost = 0.0
            }
        }
        return tanks
    }

    // ---- periods ----

    private class Span(val start: LocalDate, val end: LocalDate, val label: String, val name: String) {
        operator fun contains(date: LocalDate) = date >= start && date < end
    }

    /** Months: the last 12, ending with the current one. Years: from the first record (at most 10) to now. */
    private fun spans(period: Period, today: LocalDate, earliest: LocalDate?): List<Span> = when (period) {
        Period.Months -> (11 downTo 0).map { monthsAgo ->
            val start = today.withDayOfMonth(1).minusMonths(monthsAgo.toLong())
            Span(start, start.plusMonths(1), start.format(SHORT_MONTH), start.format(LONG_MONTH))
        }
        Period.Years -> {
            val first = maxOf(earliest?.year ?: today.year, today.year - 9)
            (first..today.year).map { year ->
                Span(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), year.toString(), year.toString())
            }
        }
    }

    private val SHORT_MONTH = DateTimeFormatter.ofPattern("MMM")
    private val LONG_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
}
