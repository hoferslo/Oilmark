package com.storagecloud.oilmark.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.storagecloud.oilmark.Launch
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.ui.common.LocalSettings
import com.storagecloud.oilmark.ui.fuel.FuelUpScreen
import com.storagecloud.oilmark.ui.garage.GarageScreen
import com.storagecloud.oilmark.ui.records.RecordEditScreen
import com.storagecloud.oilmark.ui.records.RecordListScreen
import com.storagecloud.oilmark.ui.settings.SettingsScreen
import com.storagecloud.oilmark.ui.stats.CompareScreen
import com.storagecloud.oilmark.ui.stats.StatsKind
import com.storagecloud.oilmark.ui.stats.StatsScreen
import com.storagecloud.oilmark.ui.vehicle.VehicleEditScreen
import com.storagecloud.oilmark.ui.vehicle.VehicleScreen
import kotlinx.serialization.Serializable

/*
 * Every screen of the app and how to get from one to the other.
 *
 *   Garage ─┬─ Settings
 *           ├─ VehicleEdit (add)
 *           └─ Vehicle ─┬─ VehicleEdit
 *                       ├─ Stats (tap odometer / total spent / fuel analytics)
 *                       ├─ FuelUp (quick fuel entry)
 *                       ├─ RecordEdit (quick odometer entry)
 *                       └─ Records ─┬─ RecordEdit
 *                                   └─ FuelUp (adding fuel)
 *
 * With a starred vehicle the app starts on Vehicle instead of Garage.
 */

@Serializable private object Garage
@Serializable private object SettingsRoute
@Serializable private data class VehicleRoute(val vehicleId: Int)
@Serializable private data class VehicleEdit(val vehicleId: Int? = null)
@Serializable private data class StatsRoute(val vehicleId: Int, val kind: StatsKind)
@Serializable private data class CompareRoute(val vehicleId: Int? = null)
@Serializable private data class FuelUp(val vehicleId: Int, val electric: Boolean)
@Serializable private data class Records(val vehicleId: Int, val type: RecordType, val electric: Boolean)

@Serializable
private data class RecordEdit(
    val vehicleId: Int,
    val type: RecordType,
    val electric: Boolean,
    val recordId: Int? = null,
)

@Composable
fun OilmarkNavigation(launch: Launch? = null, onLaunched: () -> Unit = {}) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    // Decided once at launch: starring a vehicle later must not restart navigation.
    val defaultVehicleId = LocalSettings.current.defaultVehicleId
    val start: Any = remember { defaultVehicleId?.let(::VehicleRoute) ?: Garage }

    /** New fuel-ups get the quick slider screen; everything else the regular form. */
    fun addRecord(vehicleId: Int, type: RecordType, electric: Boolean) {
        nav.navigate(if (type == RecordType.Fuel) FuelUp(vehicleId, electric) else RecordEdit(vehicleId, type, electric))
    }

    // From the widget or a notification: the vehicle, then the screen on top of it, so Back
    // leads somewhere sensible.
    LaunchedEffect(launch) {
        val request = launch ?: return@LaunchedEffect
        val id = request.vehicleId
        if (start != VehicleRoute(id)) nav.navigate(VehicleRoute(id))
        when (request.target) {
            Launch.Target.Vehicle -> {}
            Launch.Target.FuelUp -> nav.navigate(FuelUp(id, request.electric))
            Launch.Target.Odometer -> nav.navigate(RecordEdit(id, RecordType.Odometer, request.electric))
            Launch.Target.Reminders -> nav.navigate(Records(id, RecordType.Reminder, request.electric))
        }
        onLaunched()
    }

    NavHost(nav, startDestination = start) {
        composable<Garage> {
            GarageScreen(
                onOpenVehicle = { nav.navigate(VehicleRoute(it)) },
                onAddVehicle = { nav.navigate(VehicleEdit()) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onOpenCompare = { nav.navigate(CompareRoute()) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = back)
        }
        composable<VehicleRoute> { entry ->
            val vehicleId = entry.toRoute<VehicleRoute>().vehicleId
            VehicleScreen(
                vehicleId = vehicleId,
                onBack = {
                    // Opened straight into the starred vehicle: there is no garage behind it yet.
                    if (nav.previousBackStackEntry != null) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(Garage) { popUpTo<VehicleRoute> { inclusive = true } }
                    }
                },
                onEdit = { nav.navigate(VehicleEdit(vehicleId)) },
                onOpenRecords = { type, electric -> nav.navigate(Records(vehicleId, type, electric)) },
                onAddRecord = { type, electric -> addRecord(vehicleId, type, electric) },
                onOpenStats = { nav.navigate(StatsRoute(vehicleId, it)) },
                onOpenCompare = { nav.navigate(CompareRoute(vehicleId)) },
            )
        }
        composable<VehicleEdit> { entry ->
            VehicleEditScreen(entry.toRoute<VehicleEdit>().vehicleId, onDone = back)
        }
        composable<StatsRoute> { entry ->
            val route = entry.toRoute<StatsRoute>()
            StatsScreen(route.vehicleId, route.kind, onBack = back)
        }
        composable<CompareRoute> { entry ->
            CompareScreen(entry.toRoute<CompareRoute>().vehicleId, onBack = back)
        }
        composable<FuelUp> { entry ->
            val route = entry.toRoute<FuelUp>()
            FuelUpScreen(route.vehicleId, route.electric, onDone = back)
        }
        composable<Records> { entry ->
            val route = entry.toRoute<Records>()
            RecordListScreen(
                type = route.type,
                vehicleId = route.vehicleId,
                electric = route.electric,
                onBack = back,
                onAdd = { addRecord(route.vehicleId, route.type, route.electric) },
                onOpen = { nav.navigate(RecordEdit(route.vehicleId, route.type, route.electric, recordId = it)) },
                onOpenStats = { nav.navigate(StatsRoute(route.vehicleId, it)) },
            )
        }
        composable<RecordEdit> { entry ->
            val route = entry.toRoute<RecordEdit>()
            RecordEditScreen(route.type, route.vehicleId, route.recordId, route.electric, onDone = back)
        }
    }
}
