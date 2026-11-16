package com.storagecloud.oilmark.ui.records

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.ui.graphics.vector.ImageVector
import com.storagecloud.oilmark.data.Extra
import com.storagecloud.oilmark.data.Field
import com.storagecloud.oilmark.data.Formats
import com.storagecloud.oilmark.data.Record
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.extra

/* How each kind of record looks in the UI: its icon, its row in a list, and its sort order. */

val RecordType.icon: ImageVector
    get() = when (this) {
        RecordType.Fuel -> Icons.Outlined.LocalGasStation
        RecordType.Odometer -> Icons.Outlined.Speed
        RecordType.Service -> Icons.Outlined.Build
        RecordType.Repair -> Icons.Outlined.CarRepair
        RecordType.Upgrade -> Icons.Outlined.AutoAwesome
        RecordType.Reminder -> Icons.Outlined.NotificationsNone
        RecordType.Plan -> Icons.Outlined.Checklist
        RecordType.Tax -> Icons.AutoMirrored.Outlined.ReceiptLong
        RecordType.Supply -> Icons.Outlined.Inventory2
        RecordType.Equipment -> Icons.Outlined.Handyman
        RecordType.Note -> Icons.AutoMirrored.Outlined.StickyNote2
    }

/** The icon shown inside a form input, picked by the field's label. Unknown fields get a pencil. */
val Field.icon: ImageVector
    get() = when {
        label == "Date" -> Icons.Outlined.CalendarMonth
        label == "Due date" -> Icons.Outlined.Event
        label == "Odometer" || label == "Due at odometer" -> Icons.Outlined.Speed
        label == "Starting odometer" -> Icons.Outlined.Flag
        label == "Cost" -> Icons.Outlined.Payments
        label == "Amount" -> Icons.Outlined.LocalGasStation
        label == "Notes" || label == "Note" -> Icons.AutoMirrored.Outlined.Notes
        label.startsWith("Tags") -> Icons.Outlined.Sell
        label == "Filled to full" -> Icons.Outlined.Opacity
        label.startsWith("Missed") -> Icons.Outlined.ErrorOutline
        label == "Remind by" -> Icons.Outlined.Tune
        label == "Kind of work" -> Icons.Outlined.Category
        label == "Priority" -> Icons.Outlined.PriorityHigh
        label == "Progress" -> Icons.Outlined.Timelapse
        label == "Quantity" -> Icons.Outlined.Numbers
        label == "Part number" -> Icons.Outlined.QrCode
        label == "Supplier" -> Icons.Outlined.Store
        label == "Station" -> Icons.Outlined.Storefront
        label == "Location" -> Icons.Outlined.Place
        label == "Currently equipped" -> Icons.Outlined.CheckCircle
        label == "Pinned" -> Icons.Outlined.PushPin
        else -> Icons.Outlined.Edit // Description, Title and anything new
    }

/** The three bits of text a record shows in a list. [alert] paints [trailing] in the warning colour. */
data class RecordRow(
    val title: String,
    val subtitle: String,
    val trailing: String? = null,
    val alert: Boolean = false,
)

fun RecordType.row(record: Record, formats: Formats, electric: Boolean): RecordRow {
    val date = formats.date(record.date)
    val odometer = record.odometer?.let(formats::distance)
    val cost = record.cost?.let(formats::money)
    val description = record.description.orEmpty()

    return when (this) {
        RecordType.Service, RecordType.Repair, RecordType.Upgrade ->
            RecordRow(description, join(date, odometer), cost)

        RecordType.Tax -> RecordRow(description, date, cost)

        RecordType.Fuel -> RecordRow(
            title = formats.fuel(record.fuelConsumed ?: 0.0, electric),
            subtitle = join(
                date,
                odometer,
                record.extra(Extra.STATION),
                record.fuelEconomy?.takeIf { it > 0 }?.let { formats.economy(it, electric) },
            ),
            trailing = cost,
        )

        RecordType.Odometer -> {
            val driven = (record.odometer ?: 0) - (record.initialOdometer ?: 0)
            RecordRow(
                title = odometer.orEmpty(),
                subtitle = join(date, "+${formats.distance(driven)}".takeIf { driven > 0 }),
            )
        }

        RecordType.Reminder -> RecordRow(
            title = description,
            subtitle = join(
                record.dueDate?.takeIf { record.metric != "Odometer" }?.let { "Due ${formats.date(it)}" },
                record.dueOdometer?.takeIf { record.metric != "Date" }?.let { "At ${formats.distance(it)}" },
            ),
            trailing = pretty(record.urgency.orEmpty()),
            alert = record.urgency == "PastDue" || record.urgency == "VeryUrgent",
        )

        RecordType.Plan -> RecordRow(
            title = description,
            subtitle = join(pretty(record.type.orEmpty()), "${pretty(record.priority.orEmpty())} priority", cost),
            trailing = pretty(record.progress.orEmpty()),
        )

        RecordType.Supply -> RecordRow(
            title = description,
            subtitle = join(
                date,
                record.partQuantity?.let { "Qty ${formats.number(it)}" },
                record.partSupplier,
            ),
            trailing = cost,
        )

        RecordType.Equipment -> RecordRow(
            title = description,
            subtitle = join(
                if (record.isEquipped == true) "Equipped" else "Not equipped",
                record.distanceTraveled?.takeIf { it > 0 }?.let(formats::distance),
            ),
        )

        RecordType.Note -> RecordRow(
            title = description,
            subtitle = record.noteText.orEmpty(),
            trailing = "Pinned".takeIf { record.pinned == true },
        )
    }
}

/** Newest first for dated records; most urgent first for reminders; pinned notes on top. */
fun RecordType.sorted(records: List<Record>): List<Record> = when (this) {
    RecordType.Reminder -> records.sortedBy { urgencyOrder.indexOf(it.urgency) }
    RecordType.Plan -> records.sortedBy { progressOrder.indexOf(it.progress) }
    RecordType.Note -> records.sortedByDescending { it.pinned == true }
    RecordType.Equipment -> records.sortedByDescending { it.isEquipped == true }
    else -> records.sortedWith(compareByDescending<Record> { it.date }.thenByDescending { it.odometer })
}

private val urgencyOrder = listOf("PastDue", "VeryUrgent", "Urgent", "NotUrgent")
private val progressOrder = listOf("InProgress", "Testing", "Backlog", "Done")

/** Server constants to words: "InProgress" -> "In progress", "ServiceRecord" -> "Service". */
fun pretty(constant: String): String = constant
    .removeSuffix("Record")
    .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    .lowercase()
    .replaceFirstChar { it.uppercase() }

private fun join(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")
