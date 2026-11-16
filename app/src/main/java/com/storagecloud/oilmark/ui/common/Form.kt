package com.storagecloud.oilmark.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * Building blocks for the add/edit screens. Every input is a full-width row, so a form is just
 * a list of these inside a FormScreen. Each takes an optional icon that says what it is for.
 */

/** A screen with a back arrow, a scrolling column of inputs and a Save button at the bottom. */
@Composable
fun FormScreen(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
    error: String?,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            BackTopBar(title, onBack) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete, enabled = !saving) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                }
            }
        },
        // A second Save that is always within reach: it floats over the form and rides above
        // the keyboard, so there is no need to scroll to the bottom.
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!saving) onSave() },
                modifier = Modifier.imePadding(),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = "Save")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "* needed before you can save",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).size(18.dp), strokeWidth = 2.dp)
                }
                if (!saving) {
                    Icon(Icons.Filled.Save, contentDescription = null, Modifier.padding(end = 8.dp).size(18.dp))
                }
                Text(if (saving) "Saving…" else "Save")
            }
            // Room to scroll the last controls clear of the floating button.
            Spacer(Modifier.height(72.dp))
        }
    }
}

/** "Cost" -> "Cost *" for inputs that must be filled in before saving. */
fun String.marked(required: Boolean): String = if (required) "$this *" else this

@Composable
fun TextInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    icon: ImageVector? = null,
    keyboard: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    required: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label.marked(required)) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = keyboard,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A number box. Accepts both "." and "," as the decimal separator; reports null while empty or invalid. */
@Composable
fun NumberInput(
    label: String,
    value: Double?,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Boolean = true,
    unit: String? = null,
    icon: ImageVector? = null,
    required: Boolean = false,
) {
    // The text is kept separately so half-typed input like "12." isn't reformatted under the cursor.
    var text by rememberSaveable { mutableStateOf(value?.toPlainString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter { it.isDigit() || (decimals && (it == '.' || it == ',')) }
            onChange(text.replace(',', '.').toDoubleOrNull())
        },
        label = { Text(label.marked(required)) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        suffix = unit?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun Double.toPlainString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

/** A date box that opens a calendar. [value] is yyyy-MM-dd; optional dates can be cleared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    icon: ImageVector = Icons.Outlined.CalendarMonth,
    required: Boolean = !optional,
) {
    var open by remember { mutableStateOf(false) }
    val presses = remember { MutableInteractionSource() }
    LaunchedEffect(presses) {
        presses.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = LocalFormats.current.date(value),
        onValueChange = {},
        readOnly = true,
        label = { Text(label.marked(required)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        interactionSource = presses,
        modifier = modifier.fillMaxWidth(),
    )
    if (open) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (Formats.parseDate(value) ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                if (optional && value != null) {
                    TextButton(onClick = { onChange(null); open = false }) { Text("Clear") }
                } else {
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        ) { DatePicker(picker) }
    }
}

@Composable
fun ToggleInput(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // Lined up with the icons inside the text boxes above and below.
            Spacer(Modifier.width(12.dp))
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Pick one of a few options. [name] turns an option into the text shown on its button. */
@Composable
fun <T> ChoiceInput(
    label: String,
    options: List<T>,
    selected: T?,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    name: (T) -> String = { it.toString() },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon, contentDescription = null,
                    Modifier.padding(end = 8.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                ) {
                    Text(name(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
