package com.storagecloud.oilmark.ui.vehicle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.FuelType
import com.storagecloud.oilmark.data.LubeLoggerApi
import com.storagecloud.oilmark.data.VehicleInput
import com.storagecloud.oilmark.ui.common.ChoiceInput
import com.storagecloud.oilmark.ui.common.FormScreen
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.NumberInput
import com.storagecloud.oilmark.ui.common.TextInput
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Add a vehicle ([vehicleId] = null) or edit an existing one. */
@Composable
fun VehicleEditScreen(vehicleId: Int?, onDone: () -> Unit) {
    val api = LocalApi.current
    val model = viewModel { VehicleEditModel(api, vehicleId) }
    LaunchedEffect(model.done) { if (model.done) onDone() }

    FormScreen(
        title = if (vehicleId == null) "Add vehicle" else "Edit vehicle",
        onBack = onDone,
        onSave = model::save,
        saving = model.saving,
        error = model.error,
    ) {
        val draft = model.draft
        if (draft == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@FormScreen
        }
        val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        TextInput("Make", draft.make, { model.draft = draft.copy(make = it) }, icon = Icons.Outlined.Factory, keyboard = words, required = true)
        TextInput(
            "Model", draft.model, { model.draft = draft.copy(model = it) },
            icon = Icons.Outlined.DirectionsCar, keyboard = words, required = true,
        )
        NumberInput(
            "Year", draft.year.toDouble(), { model.draft = draft.copy(year = it?.toInt() ?: 0) },
            decimals = false, icon = Icons.Outlined.CalendarMonth, required = true,
        )
        TextInput(
            "License plate", draft.licensePlate, { model.draft = draft.copy(licensePlate = it) },
            icon = Icons.Outlined.Badge,
            required = draft.identifier == "LicensePlate",
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        ChoiceInput(
            "Runs on", FuelType.entries, draft.fuelType, { model.draft = draft.copy(fuelType = it) },
            icon = Icons.Outlined.LocalGasStation,
        )
        TextInput(
            "Tags (space separated)", draft.tags, { model.draft = draft.copy(tags = it) },
            icon = Icons.Outlined.Sell,
            keyboard = KeyboardOptions.Default,
        )
    }
}

class VehicleEditModel(private val api: LubeLoggerApi, private val vehicleId: Int?) : ViewModel() {
    /** What's in the form. Null while an existing vehicle is still loading. */
    var draft: VehicleInput? by mutableStateOf(
        if (vehicleId == null) {
            VehicleInput(
                year = LocalDate.now().year, make = "", model = "", licensePlate = "",
                fuelType = FuelType.Gasoline,
            )
        } else {
            null
        },
    )
    var saving by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var done by mutableStateOf(false)
        private set

    init {
        if (vehicleId != null) submit(finish = false) {
            val vehicle = api.vehicle(vehicleId).vehicle
            draft = VehicleInput(
                id = vehicle.id,
                year = vehicle.year,
                make = vehicle.make,
                model = vehicle.model,
                licensePlate = vehicle.licensePlate.orEmpty(),
                fuelType = vehicle.fuelType,
                tags = vehicle.tags.joinToString(" "),
                identifier = vehicle.vehicleIdentifier,
                extraFields = vehicle.extraFields,
            )
        }
    }

    fun save() {
        val vehicle = draft ?: return
        val missing = listOfNotNull(
            "Make".takeIf { vehicle.make.isBlank() },
            "Model".takeIf { vehicle.model.isBlank() },
            "Year".takeIf { vehicle.year <= 0 },
            // LubeLogger identifies a vehicle by its plate unless it was set up otherwise on the website.
            "License plate".takeIf { vehicle.identifier == "LicensePlate" && vehicle.licensePlate.isBlank() },
        )
        if (missing.isNotEmpty()) {
            error = "Still needed: ${missing.joinToString()}"
            return
        }
        submit {
            if (vehicleId == null) api.addVehicle(vehicle) else api.updateVehicle(vehicle)
        }
    }

    private fun submit(finish: Boolean = true, block: suspend () -> Unit) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                block()
                done = finish
            } catch (e: ApiException) {
                error = e.message
            }
            saving = false
        }
    }
}
