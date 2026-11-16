package com.storagecloud.oilmark.car

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Accelerometer
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.Gyroscope
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.roundToInt

/*
 * Android Auto test screen: lists everything the car is willing to tell the phone, to find
 * out what could be logged automatically (the odometer, above all).
 *
 * The service is switched off in the manifest. Settings -> "Android Auto" switches it on
 * (see ui/settings/AndroidAutoSetting.kt); until then Android Auto doesn't know the app exists.
 */

/** The permissions Android Auto wants before it hands over each kind of car data. */
val CAR_DATA_PERMISSIONS = arrayOf(
    "com.google.android.gms.permission.CAR_MILEAGE",
    "com.google.android.gms.permission.CAR_SPEED",
    "com.google.android.gms.permission.CAR_FUEL",
)

/** The entry point Android Auto connects to. */
class OilmarkCarService : CarAppService() {
    // Any head unit may connect. Fine for a personal, side-loaded app; a Play Store release
    // would list the known Android Auto hosts here instead.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = CarDataScreen(carContext)
    }
}

/**
 * One line per thing the car could share: its value if the car sent one, otherwise why not.
 * Android Auto only allows a few lines per screen, so "More" flips through pages.
 */
private class CarDataScreen(carContext: CarContext) : Screen(carContext) {

    /** Line title -> what to show. Filled in up front so the order on screen never changes. */
    private val lines = linkedMapOf(
        CAR to WAITING, ODOMETER to WAITING, SPEED to WAITING, RAW_SPEED to WAITING,
        FUEL to WAITING, BATTERY to WAITING, RANGE to WAITING, LOW_FUEL to WAITING,
        FUEL_TYPES to WAITING, UNITS to WAITING,
        COMPASS to WAITING, ACCELEROMETER to WAITING, GYROSCOPE to WAITING,
    )
    private var page = 0

    // ---- what to do with each answer from the car ----

    private val onModel = OnCarDataAvailableListener<Model> { data ->
        val parts = listOf(data.year, data.manufacturer, data.name).filter { it.status == CarValue.STATUS_SUCCESS }
        lines[CAR] = if (parts.isEmpty()) describe(data.name) else parts.joinToString(" ") { it.value.toString() }
        changed()
    }
    private val onMileage = OnCarDataAvailableListener<Mileage> { data ->
        // Despite its name this value is in kilometres (the library renamed it in a later version).
        show(ODOMETER, data.odometerMeters) { "${it.roundToInt()} km" }
        unit("distance", data.distanceDisplayUnit)
    }
    private val onSpeed = OnCarDataAvailableListener<Speed> { data ->
        show(SPEED, data.displaySpeedMetersPerSecond) { "${(it * 3.6f).roundToInt()} km/h" }
        show(RAW_SPEED, data.rawSpeedMetersPerSecond) { "${(it * 3.6f).roundToInt()} km/h" }
        unit("speed", data.speedDisplayUnit)
    }
    private val onEnergy = OnCarDataAvailableListener<EnergyLevel> { data ->
        show(FUEL, data.fuelPercent) { "${it.roundToInt()} %" }
        show(BATTERY, data.batteryPercent) { "${it.roundToInt()} %" }
        show(RANGE, data.rangeRemainingMeters) { "${(it / 1000).roundToInt()} km" }
        show(LOW_FUEL, data.energyIsLow) { if (it) "Yes, low" else "No" }
        unit("fuel", data.fuelVolumeDisplayUnit)
    }
    private val onProfile = OnCarDataAvailableListener<EnergyProfile> { data ->
        show(FUEL_TYPES, data.fuelTypes) { types -> types.joinToString { fuelName(it) }.ifEmpty { "None listed" } }
    }
    private val onCompass = OnCarDataAvailableListener<Compass> { data ->
        show(COMPASS, data.orientations) { "Heading ${it.firstOrNull()?.roundToInt() ?: "?"}°" }
    }
    private val onAccelerometer = OnCarDataAvailableListener<Accelerometer> { data ->
        show(ACCELEROMETER, data.forces) { forces -> forces.joinToString("  ") { "%.1f".format(it) } + "  m/s²" }
    }
    private val onGyroscope = OnCarDataAvailableListener<Gyroscope> { data ->
        show(GYROSCOPE, data.rotations) { turns -> turns.joinToString("  ") { "%.2f".format(it) } + "  rad/s" }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) = startListening()
            override fun onDestroy(owner: LifecycleOwner) = stopListening()
        })
    }

    private fun startListening() {
        if (carContext.carAppApiLevel < 3) {
            // Car data only exists from this version of the Android Auto app on.
            lines.keys.forEach { lines[it] = "Needs a newer Android Auto" }
            return
        }
        val hardware = carContext.getCarService(CarHardwareManager::class.java)
        val info = hardware.carInfo
        val sensors = hardware.carSensors
        val executor = ContextCompat.getMainExecutor(carContext)
        // Each one separately: a missing permission for one must not stop the others.
        attempt(CAR) { info.fetchModel(executor, onModel) }
        attempt(ODOMETER) { info.addMileageListener(executor, onMileage) }
        attempt(SPEED, RAW_SPEED) { info.addSpeedListener(executor, onSpeed) }
        attempt(FUEL, BATTERY, RANGE, LOW_FUEL) { info.addEnergyLevelListener(executor, onEnergy) }
        attempt(FUEL_TYPES) { info.fetchEnergyProfile(executor, onProfile) }
        attempt(COMPASS) { sensors.addCompassListener(CarSensors.UPDATE_RATE_NORMAL, executor, onCompass) }
        attempt(ACCELEROMETER) { sensors.addAccelerometerListener(CarSensors.UPDATE_RATE_NORMAL, executor, onAccelerometer) }
        attempt(GYROSCOPE) { sensors.addGyroscopeListener(CarSensors.UPDATE_RATE_NORMAL, executor, onGyroscope) }
        lines[UNITS] = "Not reported yet"
    }

    private fun stopListening() {
        refresher.removeCallbacksAndMessages(null)
        if (carContext.carAppApiLevel < 3) return
        val hardware = carContext.getCarService(CarHardwareManager::class.java)
        runCatching { hardware.carInfo.removeMileageListener(onMileage) }
        runCatching { hardware.carInfo.removeSpeedListener(onSpeed) }
        runCatching { hardware.carInfo.removeEnergyLevelListener(onEnergy) }
        runCatching { hardware.carSensors.removeCompassListener(onCompass) }
        runCatching { hardware.carSensors.removeAccelerometerListener(onAccelerometer) }
        runCatching { hardware.carSensors.removeGyroscopeListener(onGyroscope) }
    }

    /** Runs [ask]; if the car (or a missing permission) refuses outright, says so on those lines. */
    private fun attempt(vararg titles: String, ask: () -> Unit) {
        val problem = try {
            ask()
            return
        } catch (_: SecurityException) {
            "Not allowed - switch Android Auto off and on in Oilmark's settings and allow it"
        } catch (e: Exception) {
            "Not available (${e.javaClass.simpleName})"
        }
        titles.forEach { lines[it] = problem }
    }

    /** Puts one answer on its line: formatted if the car really sent a value, else the reason. */
    private fun <T> show(title: String, value: CarValue<T>, format: (T) -> String) {
        val data = value.value
        lines[title] = if (value.status == CarValue.STATUS_SUCCESS && data != null) format(data) else describe(value)
        changed()
    }

    private fun describe(value: CarValue<*>): String = when (value.status) {
        CarValue.STATUS_UNIMPLEMENTED -> "This car doesn't share it"
        CarValue.STATUS_UNAVAILABLE -> "Not available right now"
        else -> "Unknown"
    }

    /** The units the car's own display uses, collected from several answers into one line. */
    private val units = linkedMapOf<String, String>()

    private fun unit(what: String, value: CarValue<Int>) {
        val code = value.value
        if (value.status != CarValue.STATUS_SUCCESS || code == null) return
        units[what] = when (code) {
            CarUnit.KILOMETER -> "km"
            CarUnit.MILE -> "miles"
            CarUnit.METER -> "m"
            CarUnit.KILOMETERS_PER_HOUR -> "km/h"
            CarUnit.MILES_PER_HOUR -> "mph"
            CarUnit.METERS_PER_SEC -> "m/s"
            CarUnit.LITER -> "litres"
            CarUnit.US_GALLON -> "US gallons"
            CarUnit.IMPERIAL_GALLON -> "imperial gallons"
            else -> "code $code"
        }
        lines[UNITS] = units.entries.joinToString { "${it.key}: ${it.value}" }
        changed()
    }

    private fun fuelName(type: Int): String = when (type) {
        EnergyProfile.FUEL_TYPE_UNLEADED -> "Petrol"
        EnergyProfile.FUEL_TYPE_LEADED -> "Leaded petrol"
        EnergyProfile.FUEL_TYPE_DIESEL_1, EnergyProfile.FUEL_TYPE_DIESEL_2 -> "Diesel"
        EnergyProfile.FUEL_TYPE_BIODIESEL -> "Biodiesel"
        EnergyProfile.FUEL_TYPE_E85 -> "E85"
        EnergyProfile.FUEL_TYPE_LPG -> "LPG"
        EnergyProfile.FUEL_TYPE_CNG -> "CNG"
        EnergyProfile.FUEL_TYPE_LNG -> "LNG"
        EnergyProfile.FUEL_TYPE_ELECTRIC -> "Electric"
        EnergyProfile.FUEL_TYPE_HYDROGEN -> "Hydrogen"
        EnergyProfile.FUEL_TYPE_OTHER -> "Other"
        else -> "Unknown"
    }

    // ---- redrawing ----

    private val refresher = Handler(Looper.getMainLooper())
    private var lastRedraw = 0L
    private var redrawPending = false

    /**
     * Asks Android Auto to redraw, at most once a second: sensors report many times a second
     * and Android Auto stops listening to apps that redraw too often.
     */
    private fun changed() {
        if (redrawPending) return
        val wait = lastRedraw + 1000 - SystemClock.elapsedRealtime()
        if (wait <= 0) {
            lastRedraw = SystemClock.elapsedRealtime()
            invalidate()
        } else {
            redrawPending = true
            refresher.postDelayed({
                redrawPending = false
                lastRedraw = SystemClock.elapsedRealtime()
                invalidate()
            }, wait)
        }
    }

    override fun onGetTemplate(): Template {
        // How many lines this car's screen allows at once (usually 6).
        val perPage = if (carContext.carAppApiLevel >= 2) {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
                .coerceAtLeast(1)
        } else {
            6
        }
        val pages = lines.entries.chunked(perPage)
        page = page.coerceIn(pages.indices)

        val list = ItemList.Builder()
        for ((title, text) in pages[page]) {
            list.addItem(Row.Builder().setTitle(title).addText(text).build())
        }
        val template = ListTemplate.Builder()
            .setSingleList(list.build())
            .setTitle("Oilmark - what this car shares")
            .setHeaderAction(Action.APP_ICON)
        if (pages.size > 1) {
            template.setActionStrip(
                ActionStrip.Builder().addAction(
                    Action.Builder()
                        .setTitle("More ${page + 1}/${pages.size}")
                        .setOnClickListener {
                            page = (page + 1) % pages.size
                            invalidate()
                        }
                        .build(),
                ).build(),
            )
        }
        return template.build()
    }

    private companion object {
        const val WAITING = "Waiting for the car…"

        // Line titles
        const val CAR = "Car"
        const val ODOMETER = "Odometer"
        const val SPEED = "Speed (as the dashboard shows it)"
        const val RAW_SPEED = "Speed (actual)"
        const val FUEL = "Fuel level"
        const val BATTERY = "Battery level"
        const val RANGE = "Range left"
        const val LOW_FUEL = "Low fuel warning"
        const val FUEL_TYPES = "Runs on"
        const val UNITS = "Units on the dashboard"
        const val COMPASS = "Compass"
        const val ACCELEROMETER = "Accelerometer"
        const val GYROSCOPE = "Gyroscope"
    }
}
