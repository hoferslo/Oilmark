package com.storagecloud.oilmark.data

/** What a number in a form measures, so the UI can show the right unit next to it. */
enum class Measure { None, Distance, Fuel, Money }

/**
 * One editable field of a [Record]: how to label it, read it and write it.
 * The edit screen is generated from these, so adding a field here is all it takes to show it.
 */
sealed interface Field {
    val label: String
    val required: Boolean

    data class Text(
        override val label: String,
        override val required: Boolean = false,
        val multiline: Boolean = false,
        val get: (Record) -> String?,
        val set: Record.(String?) -> Record,
    ) : Field

    data class Number(
        override val label: String,
        override val required: Boolean = false,
        val measure: Measure = Measure.None,
        val decimals: Boolean = true,
        val get: (Record) -> Double?,
        val set: Record.(Double?) -> Record,
    ) : Field

    data class Date(
        override val label: String,
        override val required: Boolean = false,
        val get: (Record) -> String?,
        val set: Record.(String?) -> Record,
    ) : Field

    data class Toggle(
        override val label: String,
        val get: (Record) -> Boolean?,
        val set: Record.(Boolean) -> Record,
    ) : Field {
        override val required get() = false
    }

    data class Choice(
        override val label: String,
        val options: List<String>,
        val get: (Record) -> String?,
        val set: Record.(String) -> Record,
    ) : Field {
        override val required get() = true
    }
}

private object Fields {
    val date = Field.Date("Date", required = true, get = { it.date }, set = { copy(date = it) })
    val odometer = Field.Number(
        "Odometer", required = true, measure = Measure.Distance, decimals = false,
        get = { it.odometer?.toDouble() }, set = { copy(odometer = it?.toInt()) },
    )
    val description = Field.Text(
        "Description", required = true, get = { it.description }, set = { copy(description = it) },
    )
    val cost = Field.Number(
        "Cost", required = true, measure = Measure.Money, get = { it.cost }, set = { copy(cost = it) },
    )
    val notes = Field.Text("Notes", multiline = true, get = { it.notes }, set = { copy(notes = it) })
    val tags = Field.Text("Tags (space separated)", get = { it.tags }, set = { copy(tags = it) })

    /** The layout shared by service, repair and upgrade records. */
    val work = listOf(date, odometer, description, cost, notes, tags)
}

/** Every kind of record LubeLogger keeps for a vehicle. [path] is its segment in the API url. */
enum class RecordType(
    val path: String,
    /** Plural, for lists: "Repairs". */
    val title: String,
    /** Singular, for "Add repair". */
    val noun: String,
    val fields: List<Field>,
) {
    Fuel(
        "gasrecords", "Fuel", "fuel-up",
        listOf(
            Fields.date,
            Fields.odometer,
            Field.Number(
                "Amount", required = true, measure = Measure.Fuel,
                get = { it.fuelConsumed }, set = { copy(fuelConsumed = it) },
            ),
            Fields.cost,
            Field.Toggle("Filled to full", get = { it.isFillToFull }, set = { copy(isFillToFull = it) }),
            Field.Toggle("Missed a fuel-up before this", get = { it.missedFuelUp }, set = { copy(missedFuelUp = it) }),
            Fields.notes,
            Fields.tags,
        ),
    ),
    Odometer(
        "odometerrecords", "Odometer", "odometer reading",
        listOf(
            Fields.date,
            Fields.odometer,
            Field.Number(
                "Starting odometer", measure = Measure.Distance, decimals = false,
                get = { it.initialOdometer?.toDouble() }, set = { copy(initialOdometer = it?.toInt()) },
            ),
            Fields.notes,
            Fields.tags,
        ),
    ),
    Service("servicerecords", "Service", "service", Fields.work),
    Repair("repairrecords", "Repairs", "repair", Fields.work),
    Upgrade("upgraderecords", "Upgrades", "upgrade", Fields.work),
    Reminder(
        "reminders", "Reminders", "reminder",
        listOf(
            Fields.description,
            Field.Choice(
                "Remind by", listOf("Date", "Odometer", "Both"),
                get = { it.metric }, set = { copy(metric = it) },
            ),
            Field.Date("Due date", get = { it.dueDate }, set = { copy(dueDate = it) }),
            Field.Number(
                "Due at odometer", measure = Measure.Distance, decimals = false,
                get = { it.dueOdometer?.toDouble() }, set = { copy(dueOdometer = it?.toInt()) },
            ),
            Fields.notes,
            Fields.tags,
        ),
    ),
    Plan(
        "planrecords", "Plans", "plan",
        listOf(
            Fields.description,
            Field.Choice(
                "Kind of work", listOf("ServiceRecord", "RepairRecord", "UpgradeRecord"),
                get = { it.type }, set = { copy(type = it) },
            ),
            Field.Choice(
                "Priority", listOf("Critical", "Normal", "Low"),
                get = { it.priority }, set = { copy(priority = it) },
            ),
            // "Done" is missing on purpose: the API refuses it. Plans are completed on the website,
            // which turns them into a real service/repair/upgrade record.
            Field.Choice(
                "Progress", listOf("Backlog", "InProgress", "Testing"),
                get = { it.progress }, set = { copy(progress = it) },
            ),
            Fields.cost,
            Fields.notes,
        ),
    ),
    Tax(
        "taxrecords", "Taxes & fees", "tax or fee",
        listOf(Fields.date, Fields.description, Fields.cost, Fields.notes, Fields.tags),
    ),
    Supply(
        "supplyrecords", "Supplies", "supply",
        listOf(
            Fields.date,
            Fields.description,
            // Directly under the description: the edit screen puts a "swap" button between the two.
            Field.Text("Part number", get = { it.partNumber }, set = { copy(partNumber = it) }),
            Field.Number("Quantity", required = true, get = { it.partQuantity }, set = { copy(partQuantity = it) }),
            Fields.cost,
            Field.Text("Supplier", get = { it.partSupplier }, set = { copy(partSupplier = it) }),
            Fields.notes,
            Fields.tags,
        ),
    ),
    Equipment(
        "equipmentrecords", "Equipment", "equipment",
        listOf(
            Fields.description,
            Field.Toggle("Currently equipped", get = { it.isEquipped }, set = { copy(isEquipped = it) }),
            Fields.notes,
            Fields.tags,
        ),
    ),
    Note(
        "notes", "Notes", "note",
        listOf(
            Field.Text("Title", required = true, get = { it.description }, set = { copy(description = it) }),
            Field.Text("Note", required = true, multiline = true, get = { it.noteText }, set = { copy(noteText = it) }),
            Field.Toggle("Pinned", get = { it.pinned }, set = { copy(pinned = it) }),
            Fields.tags,
        ),
    );

    /** Whether photos and files can be attached (the server has no attachments on reminders). */
    val hasFiles: Boolean get() = this != Reminder

    /** A sensible starting point for the "add" form. [today] is yyyy-MM-dd. */
    fun blank(today: String, lastOdometer: Int? = null): Record = when (this) {
        Fuel -> Record(date = today, isFillToFull = true, missedFuelUp = false)
        // A new reading starts where the last one ended; the user only nudges it up.
        Odometer -> Record(date = today, odometer = lastOdometer, initialOdometer = lastOdometer)
        // Work is usually logged the day it is done, so the car's current odometer is the best guess.
        Service, Repair, Upgrade -> Record(date = today, odometer = lastOdometer)
        Tax -> Record(date = today)
        Supply -> Record(date = today, partQuantity = 1.0)
        Reminder -> Record(metric = "Date")
        Plan -> Record(type = "ServiceRecord", priority = "Normal", progress = "Backlog", cost = 0.0)
        Equipment -> Record(isEquipped = true)
        Note -> Record(pinned = false)
    }

    /** Whether the "add" form should start with the vehicle's last odometer filled in. */
    val startsAtLastOdometer: Boolean get() = this == Odometer || this == Service || this == Repair || this == Upgrade

    /**
     * Whether [field] must be filled in for [record]. Usually fixed per field; a reminder needs
     * its due date and/or due odometer depending on what it reminds by.
     */
    fun isRequired(field: Field, record: Record): Boolean = field.required || (
        this == Reminder && when (field.label) {
            "Due date" -> record.metric != "Odometer"
            "Due at odometer" -> record.metric != "Date"
            else -> false
        }
        )

    /** Labels of the required fields that are still empty. An empty list means it can be saved. */
    fun missingFields(record: Record): List<String> =
        fields.filter { isRequired(it, record) && it.isEmpty(record) }.map { it.label }
}

private fun Field.isEmpty(record: Record): Boolean = when (this) {
    is Field.Text -> get(record).isNullOrBlank()
    is Field.Number -> get(record) == null
    is Field.Date -> get(record).isNullOrBlank()
    is Field.Toggle, is Field.Choice -> false
}
