package com.storagecloud.oilmark.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val today = LocalDate.parse("2026-11-16")

    @Test
    fun spendingIsSplitByMonthAndType() {
        val buckets = Stats.spending(
            mapOf(
                RecordType.Fuel to listOf(Record(date = "2026-11-02", cost = 60.0), Record(date = "2026-10-20", cost = 50.0)),
                RecordType.Service to listOf(Record(date = "2026-11-10", cost = 120.0)),
                RecordType.Tax to listOf(Record(date = "2024-01-01", cost = 999.0)), // outside the 12 months
            ),
            Period.Months, today,
        )
        assertEquals(12, buckets.size)
        assertEquals("November 2026", buckets.last().name)
        assertEquals(listOf(60.0, 120.0, 0.0, 0.0, 0.0), buckets.last().values)
        assertEquals(50.0, buckets[10].total, 0.0)
        assertEquals(230.0, buckets.sumOf { it.total }, 0.0)
    }

    @Test
    fun spendingByYearStartsAtTheFirstRecord() {
        val buckets = Stats.spending(
            mapOf(RecordType.Repair to listOf(Record(date = "2024-05-01", cost = 10.0), Record(date = "2026-01-01", cost = 5.0))),
            Period.Years, today,
        )
        assertEquals(listOf("2024", "2025", "2026"), buckets.map { it.label })
        assertEquals(listOf(10.0, 0.0, 5.0), buckets.map { it.total })
    }

    @Test
    fun distanceComesFromOdometerReadings() {
        val readings = listOf(
            Record(date = "2026-09-05", odometer = 10_000),
            Record(date = "2026-09-25", odometer = 10_400),
            Record(date = "2026-11-03", odometer = 11_500),
            Record(date = "2026-11-10", odometer = 11_900),
        )
        val months = Stats.distance(readings, Period.Months, today)
        // Sep 25 (10,400) -> Nov 3 (11,500) is 1,100 over 39 days, shared out evenly:
        assertEquals(569.0, months[9].total, 0.0)   // September: 400 + 6 days' share
        assertEquals(874.0, months[10].total, 0.0)  // October: no reading of its own, 31 days' share
        assertEquals(456.0, months[11].total, 0.0)  // November: 2 days' share + 400

        assertEquals(listOf(1900.0), Stats.distance(readings, Period.Years, today).map { it.total })

        // The odometer itself, at the end of each month: nothing before the first reading,
        // an estimate between readings, the latest reading for the current month.
        val odometer = Stats.odometer(readings, Period.Months, today).map { it.total }
        assertEquals(0.0, odometer[8], 0.0)        // August: before the first reading
        assertEquals(10_569.0, odometer[9], 0.0)   // end of September
        assertEquals(11_444.0, odometer[10], 0.0)  // end of October
        assertEquals(11_900.0, odometer[11], 0.0)  // November so far
    }

    @Test
    fun distanceSummary() {
        val stats = Stats.distanceStats(
            listOf(
                Record(date = "2026-01-01", odometer = 10_000),
                Record(date = "2026-10-20", odometer = 18_000),
                Record(date = "2026-11-11", odometer = 19_000),
            ),
            today,
        )
        assertEquals(9_000, stats.total)
        assertEquals(9_000, stats.thisYear)
        assertEquals(1_082, stats.last30Days) // 1,000 since Oct 20 plus 3 days' share of the stretch before
        assertEquals(9_000.0 / 314, stats.perDay!!, 0.001) // Jan 1 -> Nov 11
    }

    @Test
    fun consumptionIsMeasuredFromFullTankToFullTank() {
        fun fill(date: String, odometer: Int, fuel: Double, cost: Double, full: Boolean = true, missed: Boolean = false) =
            Record(date = date, odometer = odometer, fuelConsumed = fuel, cost = cost, isFillToFull = full, missedFuelUp = missed)

        val stats = Stats.fuel(
            listOf(
                fill("2026-09-01", 10_000, 40.0, 60.0),               // first full tank: the starting point
                fill("2026-09-10", 10_300, 15.0, 24.0, full = false), // partial: counted into the next full one
                fill("2026-09-20", 10_600, 27.0, 40.5),               // 600 km on 42 L
                fill("2026-10-05", 11_100, 30.0, 48.0),               // 500 km on 30 L
                fill("2026-10-25", 11_900, 35.0, 52.5, missed = true), // unknown fuel in between: not measured
                fill("2026-11-10", 12_400, 36.0, 54.0),               // 500 km on 36 L
            ),
        )
        assertEquals(6, stats.fillUps)
        assertEquals(183.0, stats.totalFuel, 0.0)
        assertEquals(listOf(600, 500, 500), stats.tanks.map { it.distance })
        assertEquals(listOf(42.0, 30.0, 36.0), stats.tanks.map { it.fuel })
        assertEquals(1_600, stats.measuredDistance)
        assertEquals(500, stats.bestTank!!.distance)
        assertEquals(30.0, stats.bestTank!!.fuel, 0.0)
        assertEquals(36.0, stats.worstTank!!.fuel, 0.0)
        assertEquals(1.5, stats.lastPrice!!, 0.0)
        assertEquals(1.6, stats.highestPrice!!, 0.0001)

        // 108 L over 1600 km
        assertEquals("6.75 L/100km", Formats(Units.Metric).consumption(stats.measuredDistance, stats.measuredFuel, electric = false))

        val november = Stats.fuelPeriods(
            listOf(fill("2026-10-05", 11_100, 30.0, 48.0), fill("2026-11-10", 11_600, 36.0, 54.0)),
            Period.Months, today,
        ).last()
        assertEquals(36.0, november.fuel, 0.0)
        assertEquals(500, november.measuredDistance)
    }

    @Test
    fun totalsCoverEverythingInTheRange() {
        val records = mapOf(
            RecordType.Odometer to listOf(Record(date = "2026-01-01", odometer = 10_000)),
            RecordType.Fuel to listOf(
                Record(date = "2026-02-01", odometer = 11_000, fuelConsumed = 40.0, cost = 60.0, isFillToFull = true),
                Record(date = "2026-03-01", odometer = 11_500, fuelConsumed = 35.0, cost = 56.0, isFillToFull = true),
            ),
            RecordType.Service to listOf(Record(date = "2026-03-10", odometer = 12_000, cost = 84.0)),
            RecordType.Tax to listOf(Record(date = "2025-12-31", cost = 999.0)), // before the range
        )
        val totals = Stats.totals(records, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-11"))
        assertEquals(2_000, totals.distance)
        assertEquals(200.0, totals.spent, 0.001)
        assertEquals(10.0, totals.costPer100!!, 0.001)       // 200 over 2,000 km
        assertEquals(5.8, totals.fuelCostPer100!!, 0.001)    // 116 over 2,000 km
        assertEquals(500, totals.measuredDistance)          // one full-to-full stretch
        assertEquals(35.0, totals.measuredFuel, 0.0)
        assertEquals(116.0 / 75.0, totals.averageFuelPrice!!, 0.0001)
        assertEquals(2025, Stats.firstYear(records))
    }
}
