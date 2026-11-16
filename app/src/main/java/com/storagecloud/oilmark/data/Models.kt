package com.storagecloud.oilmark.data

import kotlinx.serialization.Serializable

/*
 * Plain data classes mirroring LubeLogger's JSON (requested with the `culture-invariant` header,
 * so numbers are real numbers and dates are always yyyy-MM-dd).
 */

@Serializable
data class Vehicle(
    val id: Int = 0,
    val imageLocation: String? = null,
    val year: Int = 0,
    val make: String = "",
    val model: String = "",
    val licensePlate: String? = null,
    val isElectric: Boolean = false,
    val isDiesel: Boolean = false,
    val tags: List<String> = emptyList(),
    val extraFields: List<ExtraField> = emptyList(),
    val vehicleIdentifier: String = "LicensePlate",
) {
    val name: String get() = "$year $make $model"
    val fuelType: FuelType
        get() = when {
            isElectric -> FuelType.Electric
            isDiesel -> FuelType.Diesel
            else -> FuelType.Gasoline
        }
}

enum class FuelType { Gasoline, Diesel, Electric }

/** A vehicle plus the totals LubeLogger shows on its dashboard (`/api/vehicle/info`). */
@Serializable
data class VehicleInfo(
    val vehicleData: Vehicle = Vehicle(),
    val pastDueReminderCount: Int = 0,
    /** The reminder that comes up first, if there is any. */
    val nextReminder: Record? = null,
    val veryUrgentReminderCount: Int = 0,
    val urgentReminderCount: Int = 0,
    val notUrgentReminderCount: Int = 0,
    val serviceRecordCount: Int = 0,
    val serviceRecordCost: Double = 0.0,
    val repairRecordCount: Int = 0,
    val repairRecordCost: Double = 0.0,
    val upgradeRecordCount: Int = 0,
    val upgradeRecordCost: Double = 0.0,
    val taxRecordCount: Int = 0,
    val taxRecordCost: Double = 0.0,
    val gasRecordCount: Int = 0,
    val gasRecordCost: Double = 0.0,
    val lastReportedOdometer: Int = 0,
    val planRecordBackLogCount: Int = 0,
    val planRecordInProgressCount: Int = 0,
    val planRecordTestingCount: Int = 0,
    val planRecordDoneCount: Int = 0,
) {
    val vehicle: Vehicle get() = vehicleData
    val totalCost: Double
        get() = serviceRecordCost + repairRecordCost + upgradeRecordCost + taxRecordCost + gasRecordCost

    /** Reminders that need attention soon (everything except "not urgent"). */
    val dueReminderCount: Int
        get() = pastDueReminderCount + veryUrgentReminderCount + urgentReminderCount
    val reminderCount: Int get() = dueReminderCount + notUrgentReminderCount
    val openPlanCount: Int
        get() = planRecordBackLogCount + planRecordInProgressCount + planRecordTestingCount
}

/** Body for adding (id = null) or updating a vehicle. */
@Serializable
data class VehicleInput(
    val id: Int? = null,
    val year: Int,
    val make: String,
    val model: String,
    val licensePlate: String,
    val fuelType: FuelType,
    /** Space separated. */
    val tags: String = "",
    val identifier: String = "LicensePlate",
    val extraFields: List<ExtraField> = emptyList(),
)

@Serializable
data class ExtraField(val name: String = "", val value: String = "")

/*
 * Fields LubeLogger has no column for (station name, location) go into a record's
 * "extra fields": LubeLogger keeps any name/value pair it is sent. If the same names are
 * set up under Settings -> Extra Fields on the LubeLogger website, they show there too.
 */

/** The value of the extra field called [name], or null when it is missing or blank. */
fun Record.extra(name: String): String? =
    extraFields.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }

/** A copy with the extra field [name] set to [value] (removed when [value] is blank). */
fun Record.withExtra(name: String, value: String?): Record {
    val others = extraFields.filterNot { it.name.equals(name, ignoreCase = true) }
    return copy(extraFields = if (value.isNullOrBlank()) others else others + ExtraField(name, value.trim()))
}

/** Names of the extra fields the app itself uses. */
object Extra {
    const val STATION = "Station"
    /** "latitude,longitude", e.g. "46.056946,14.505751". */
    const val LOCATION = "Location"
}

@Serializable
data class Attachment(val name: String = "", val location: String = "")

/**
 * One record of any [RecordType].
 *
 * LubeLogger's eleven record kinds overlap heavily, so instead of eleven near-identical classes
 * there is a single one where every field is optional. [RecordType.fields] says which fields
 * a given kind actually uses. Fields left `null` are simply not sent to the server.
 */
@Serializable
data class Record(
    val id: Int = 0,
    val vehicleId: Int = 0,

    // Shared by most kinds
    val date: String? = null,
    val description: String? = null,
    val notes: String? = null,
    /** Space separated. */
    val tags: String? = null,
    val cost: Double? = null,
    val odometer: Int? = null,
    val extraFields: List<ExtraField> = emptyList(),
    val files: List<Attachment> = emptyList(),

    // Odometer
    val initialOdometer: Int? = null,

    // Fuel
    val fuelConsumed: Double? = null,
    val isFillToFull: Boolean? = null,
    val missedFuelUp: Boolean? = null,
    /** Read-only, computed by the server. */
    val fuelEconomy: Double? = null,

    // Supplies
    val partNumber: String? = null,
    val partSupplier: String? = null,
    val partQuantity: Double? = null,

    // Reminders
    val dueDate: String? = null,
    val dueOdometer: Int? = null,
    /** What the reminder goes by: "Date", "Odometer" or "Both". */
    val metric: String? = null,
    /** Only used while reading from the server, see LubeLoggerApi.fromServer. */
    internal val userMetric: String? = null,
    /** Read-only: "NotUrgent", "Urgent", "VeryUrgent" or "PastDue". */
    val urgency: String? = null,
    val dueDays: Int? = null,
    val dueDistance: Int? = null,

    // Equipment
    val isEquipped: Boolean? = null,
    val distanceTraveled: Int? = null,

    // Notes
    val noteText: String? = null,
    val pinned: Boolean? = null,

    // Plans
    val type: String? = null,
    val priority: String? = null,
    val progress: String? = null,
    val dateCreated: String? = null,
)

@Serializable
data class UserInfo(val username: String = "", val emailAddress: String = "")

@Serializable
data class ServerInfo(
    val currentVersion: String = "",
    val currencySymbol: String = "$",
    val decimalSeparator: String = ".",
)

/** The envelope LubeLogger answers every add/update/delete with. */
@Serializable
internal data class OperationResult(
    val success: Boolean = false,
    val message: String = "",
    val additionalData: Ids? = null,
) {
    @Serializable
    data class Ids(val recordId: Int? = null, val vehicleId: Int? = null)
}
