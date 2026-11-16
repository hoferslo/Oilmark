package com.storagecloud.oilmark.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelPricesTest {
    private val slovenia = CountryFuelPrices("SI", "Slovenia", "EUR", gasoline = 1.506, diesel = 1.848)
    private val usa = CountryFuelPrices(
        "US", "United States", "USD", gasoline = 3.9422, diesel = 6.0938,
        gasolineUnit = "gallon", dieselUnit = "gallon",
    )

    @Test
    fun priceMatchesTheVehiclesFuel() {
        assertEquals(1.506, slovenia.pricePer(Units.Metric, FuelType.Gasoline)!!, 0.0)
        assertEquals(1.848, slovenia.pricePer(Units.Metric, FuelType.Diesel)!!, 0.0)
        assertNull(slovenia.pricePer(Units.Metric, FuelType.Electric))
    }

    @Test
    fun pricesSurviveBeingSavedAndReadBack() {
        val saved = Json.encodeToString(usa)
        assertEquals(usa, Json.decodeFromString<CountryFuelPrices>(saved))
    }

    @Test
    fun priceIsGivenPerTheAppsFuelUnit() {
        assertEquals(3.942, usa.pricePer(Units.US, FuelType.Gasoline)!!, 0.0)       // gallon -> gallon
        assertEquals(1.041, usa.pricePer(Units.Metric, FuelType.Gasoline)!!, 0.0)   // gallon -> litre
        assertEquals(5.701, slovenia.pricePer(Units.US, FuelType.Gasoline)!!, 0.0)  // litre -> gallon
    }
}
