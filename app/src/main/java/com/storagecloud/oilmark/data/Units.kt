package com.storagecloud.oilmark.data

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** LubeLogger stores plain numbers; the unit system only decides how they are labelled. */
enum class Units(val label: String, val distance: String, val fuel: String, val economy: String) {
    Metric("Metric (km, L)", "km", "L", "L/100km"),
    US("US (mi, gal)", "mi", "gal", "mpg"),
    UK("UK (mi, L)", "mi", "L", "mpg"),
}

/** Turns raw values into text for the screen. One instance is shared through the whole UI. */
class Formats(val units: Units = Units.Metric, val currency: String = "$") {

    private val whole = NumberFormat.getIntegerInstance()
    private val decimal = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    private val price = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val dateStyle = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun distance(value: Int): String = "${whole.format(value)} ${units.distance}"

    /** "$12.50", or "CHF 12.50" when the symbol is letters. */
    fun money(value: Double): String {
        val gap = if (currency.lastOrNull()?.isLetter() == true) " " else ""
        return "$currency$gap${price.format(value)}"
    }

    fun number(value: Double): String = decimal.format(value)

    fun fuel(value: Double, electric: Boolean): String = "${decimal.format(value)} ${fuelUnit(electric)}"

    fun economy(value: Double, electric: Boolean): String = "${decimal.format(value)} ${economyUnit(electric)}"

    fun fuelUnit(electric: Boolean): String = if (electric) "kWh" else units.fuel

    /**
     * Consumption over [distance] using [fuel], the way the unit system states it:
     * "6.8 L/100km" (lower is better) or "34.5 mpg" (higher is better).
     */
    fun consumption(distance: Int, fuel: Double, electric: Boolean): String =
        economy(consumptionValue(distance, fuel, electric), electric)

    /** The number inside [consumption], for charts. */
    fun consumptionValue(distance: Int, fuel: Double, electric: Boolean): Double = when {
        distance <= 0 || fuel <= 0 -> 0.0
        units == Units.Metric -> fuel / distance * 100
        // UK: fuel is bought in litres but economy is stated in miles per imperial gallon.
        units == Units.UK && !electric -> distance / (fuel / 4.54609)
        else -> distance / fuel
    }

    /** "$8.40 / 100 km" */
    fun costPer100(cost: Double, distance: Int): String =
        if (distance > 0) "${money(cost / distance * 100)} / 100 ${units.distance}" else "–"

    /** "$1.506/L" - fuel prices are quoted to three decimals. */
    fun pricePerUnit(value: Double, electric: Boolean): String {
        val gap = if (currency.lastOrNull()?.isLetter() == true) " " else ""
        return "$currency$gap${String.format(java.util.Locale.getDefault(), "%.3f", value)}/${fuelUnit(electric)}"
    }

    /** The unit consumption is stated in: "L/100km", "mpg", ... */
    fun economyUnit(electric: Boolean): String = when {
        !electric -> units.economy
        units == Units.Metric -> "kWh/100km"
        else -> "mi/kWh"
    }

    /** "2026-11-16" -> "Nov 16, 2026" in the phone's language. Unknown input is shown as-is. */
    fun date(iso: String?): String = parseDate(iso)?.format(dateStyle) ?: iso.orEmpty()

    companion object {
        fun parseDate(iso: String?): LocalDate? = try {
            iso?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
