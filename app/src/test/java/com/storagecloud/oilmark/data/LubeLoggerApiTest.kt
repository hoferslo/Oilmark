package com.storagecloud.oilmark.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Runs against a real LubeLogger (the local Docker container by default, see app/build.gradle.kts).
 * Each test works on its own throwaway vehicle, so existing data is never touched.
 */
class LubeLoggerApiTest {

    private val api = LubeLoggerApi(Server(System.getProperty("lubelogger.url") ?: "http://localhost:8080"))
    private var vehicleId = 0

    @Before
    fun createVehicle() = runTest {
        vehicleId = api.addVehicle(
            VehicleInput(
                year = 2020, make = "Test", model = "Car", licensePlate = "TEST-1",
                fuelType = FuelType.Diesel, tags = "oilmark test",
            ),
        )
        assertTrue(vehicleId > 0)
    }

    @After
    fun deleteVehicle() = runTest {
        api.deleteVehicle(vehicleId)
        assertFalse(api.vehicles().any { it.vehicle.id == vehicleId })
    }

    @Test
    fun serverAnswers() = runTest {
        assertTrue(api.serverInfo().currentVersion.isNotBlank())
        assertNotNull(api.whoAmI())
    }

    @Test
    fun vehicleCanBeReadAndUpdated() = runTest {
        val vehicle = api.vehicle(vehicleId).vehicle
        assertEquals("2020 Test Car", vehicle.name)
        assertEquals(FuelType.Diesel, vehicle.fuelType)
        assertEquals(listOf("oilmark", "test"), vehicle.tags)

        api.updateVehicle(
            VehicleInput(
                id = vehicleId, year = 2021, make = "Test", model = "Car 2", licensePlate = "TEST-2",
                fuelType = FuelType.Electric,
            ),
        )
        val updated = api.vehicle(vehicleId).vehicle
        assertEquals("2021 Test Car 2", updated.name)
        assertEquals("TEST-2", updated.licensePlate)
        assertEquals(FuelType.Electric, updated.fuelType)
    }

    /** Add, read, update and delete one record of every type, the same way the app's forms do. */
    @Test
    fun everyRecordTypeRoundTrips() = runTest {
        for (type in RecordType.entries) {
            val draft = sample(type)
            assertEquals("$type sample is incomplete", emptyList<String>(), type.missingFields(draft))

            val id = api.addRecord(type, vehicleId, draft)
            assertTrue("$type: no id returned", id > 0)

            val saved = api.record(type, vehicleId, id)
            for (field in type.fields) {
                assertEquals("$type.${field.label} after add", field.read(draft), field.read(saved))
            }

            val edited = type.fields.fold(saved) { record, field -> field.change(record) }
            api.updateRecord(type, edited)
            val reloaded = api.record(type, vehicleId, id)
            for (field in type.fields) {
                assertEquals("$type.${field.label} after update", field.read(edited), field.read(reloaded))
            }

            api.deleteRecord(type, id)
            assertTrue("$type: not deleted", api.records(type, vehicleId).none { it.id == id })
        }
    }

    @Test
    fun dashboardTotalsFollowRecords() = runTest {
        api.addRecord(RecordType.Service, vehicleId, sample(RecordType.Service))
        api.addRecord(RecordType.Fuel, vehicleId, sample(RecordType.Fuel))
        val info = api.vehicle(vehicleId)
        assertEquals(1, info.serviceRecordCount)
        assertEquals(1, info.gasRecordCount)
        assertEquals(12.5 + 12.5, info.totalCost, 0.001)
    }

    @Test
    fun photosCanBeAttachedToRecords() = runTest {
        val uploaded = api.upload(listOf(Upload("receipt.png", tinyPng, "image/png")))
        assertEquals("receipt.png", uploaded.single().name)

        val draft = sample(RecordType.Service).copy(files = uploaded)
        val id = api.addRecord(RecordType.Service, vehicleId, draft)
        val saved = api.record(RecordType.Service, vehicleId, id)
        assertEquals(uploaded, saved.files)

        // Saving again without the file removes it.
        api.updateRecord(RecordType.Service, saved.copy(files = emptyList()))
        assertEquals(emptyList<Attachment>(), api.record(RecordType.Service, vehicleId, id).files)
    }

    @Test
    fun vehiclePhotoCanBeChangedWithoutLosingAnythingElse() = runTest {
        val before = api.vehicle(vehicleId).vehicle
        api.setVehiclePhoto(vehicleId, Upload("car.png", tinyPng, "image/png"))
        val after = api.vehicle(vehicleId).vehicle

        assertTrue("photo not set: ${after.imageLocation}", after.imageLocation!!.startsWith("/images/"))
        assertEquals(before.copy(imageLocation = after.imageLocation), after)
    }

    @Test
    fun failuresAreReadable() = runTest {
        try {
            api.deleteRecord(RecordType.Note, 999_999)
            fail("deleting a missing record should fail")
        } catch (e: ApiException) {
            assertEquals("Invalid Record Id", e.message)
        }

        try {
            LubeLoggerApi(Server("http://localhost:1")).vehicles()
            fail("an unreachable server should fail")
        } catch (e: ApiException) {
            assertEquals("Can't reach the server at http://localhost:1", e.message)
        }
    }

    /** A valid 1x1 pixel PNG. */
    private val tinyPng = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
    )

    // ---- helpers: fill in / change every field of a form generically ----

    private fun sample(type: RecordType): Record =
        type.fields.fold(type.blank(today = "2026-03-01")) { record, field ->
            when (field) {
                is Field.Text -> field.set(record, "${field.label} text")
                is Field.Number -> field.set(record, if (field.decimals) 12.5 else 1200.0)
                is Field.Date -> field.set(record, "2026-03-01")
                is Field.Toggle -> field.set(record, true)
                is Field.Choice -> field.set(record, field.options.last())
            }
        }

    private fun Field.change(record: Record): Record = when (this) {
        is Field.Text -> set(record, "changed")
        is Field.Number -> set(record, if (decimals) 99.75 else 3400.0)
        is Field.Date -> set(record, "2026-04-15")
        is Field.Toggle -> set(record, false)
        is Field.Choice -> set(record, options.first())
    }

    private fun Field.read(record: Record): Any? = when (this) {
        is Field.Text -> get(record)
        is Field.Number -> get(record)
        is Field.Date -> get(record)
        is Field.Toggle -> get(record)
        is Field.Choice -> get(record)
    }
}
