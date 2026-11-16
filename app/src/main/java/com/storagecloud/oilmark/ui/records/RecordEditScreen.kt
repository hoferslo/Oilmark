package com.storagecloud.oilmark.ui.records

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.Field
import com.storagecloud.oilmark.data.LubeLoggerApi
import com.storagecloud.oilmark.data.Measure
import com.storagecloud.oilmark.data.Record
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.ui.common.ChoiceInput
import com.storagecloud.oilmark.ui.common.ConfirmDeleteDialog
import com.storagecloud.oilmark.ui.common.DateInput
import com.storagecloud.oilmark.ui.common.FormScreen
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.NumberInput
import com.storagecloud.oilmark.ui.common.TextInput
import com.storagecloud.oilmark.ui.common.ToggleInput
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Add a record ([recordId] = null) or edit an existing one. The form is built from
 * [RecordType.fields], so this one screen covers every record type.
 */
@Composable
fun RecordEditScreen(
    type: RecordType,
    vehicleId: Int,
    recordId: Int?,
    electric: Boolean,
    onDone: () -> Unit,
) {
    val api = LocalApi.current
    val formats = LocalFormats.current
    val model = viewModel { RecordEditModel(api, type, vehicleId, recordId) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(model.done) { if (model.done) onDone() }

    FormScreen(
        title = if (recordId == null) "Add ${type.noun}" else "Edit ${type.noun}",
        onBack = onDone,
        onSave = model::save,
        saving = model.saving,
        error = model.error,
        onDelete = if (recordId != null) ({ confirmDelete = true }) else null,
    ) {
        val draft = model.draft
        if (draft == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@FormScreen
        }

        for (field in type.fields) {
            // Supplies: a button between description and part number that swaps the two,
            // for when they were typed (or imported) the wrong way round.
            if (type == RecordType.Supply && field.label == "Part number") {
                TextButton(
                    onClick = {
                        model.draft = draft.copy(description = draft.partNumber, partNumber = draft.description)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null, Modifier.padding(end = 8.dp))
                    Text("Swap description and part number")
                }
            }
            when (field) {
                is Field.Text -> TextInput(
                    field.label, field.get(draft).orEmpty(), { model.draft = field.set(draft, it) },
                    multiline = field.multiline,
                    icon = field.icon,
                    required = type.isRequired(field, draft),
                )
                is Field.Number -> NumberInput(
                    field.label, field.get(draft), { model.draft = field.set(draft, it) },
                    decimals = field.decimals,
                    unit = when (field.measure) {
                        Measure.Distance -> formats.units.distance
                        Measure.Fuel -> formats.fuelUnit(electric)
                        Measure.Money -> formats.currency
                        Measure.None -> null
                    },
                    icon = field.icon,
                    required = type.isRequired(field, draft),
                )
                is Field.Date -> DateInput(
                    field.label, field.get(draft), { model.draft = field.set(draft, it) },
                    optional = !field.required,
                    icon = field.icon,
                    required = type.isRequired(field, draft),
                )
                is Field.Toggle -> ToggleInput(
                    field.label, field.get(draft) == true, { model.draft = field.set(draft, it) },
                    icon = field.icon,
                )
                is Field.Choice -> ChoiceInput(
                    field.label, field.options, field.get(draft), { model.draft = field.set(draft, it) },
                    icon = field.icon,
                    name = ::pretty,
                )
            }
        }

        if (type.hasFiles) {
            // Read the draft fresh: an upload can finish after other fields were edited.
            Attachments(draft.files) { files -> model.draft = model.draft?.copy(files = files) }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = "this ${type.noun}",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                model.delete()
            },
        )
    }
}

class RecordEditModel(
    private val api: LubeLoggerApi,
    private val type: RecordType,
    private val vehicleId: Int,
    private val recordId: Int?,
) : ViewModel() {
    /** What's in the form. Null while loading (an existing record, or the odometer to start from). */
    var draft: Record? by mutableStateOf(
        if (recordId == null && !type.startsAtLastOdometer) type.blank(today = LocalDate.now().toString()) else null,
    )
    var saving by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var done by mutableStateOf(false)
        private set

    init {
        when {
            recordId != null -> submit(finish = false) {
                draft = api.record(type, vehicleId, recordId)
            }
            // New record that has an odometer: start it at the vehicle's current one.
            type.startsAtLastOdometer -> viewModelScope.launch {
                val lastOdometer = try {
                    api.vehicle(vehicleId).lastReportedOdometer.takeIf { it > 0 }
                } catch (_: ApiException) {
                    null // offline: the form just starts without it
                }
                draft = type.blank(today = LocalDate.now().toString(), lastOdometer = lastOdometer)
            }
        }
    }

    fun save() {
        val record = draft ?: return
        val missing = type.missingFields(record)
        if (missing.isNotEmpty()) {
            error = "Still needed: ${missing.joinToString()}"
            return
        }
        submit {
            if (recordId == null) api.addRecord(type, vehicleId, record) else api.updateRecord(type, record)
        }
    }

    fun delete() {
        if (recordId != null) submit { api.deleteRecord(type, recordId) }
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
