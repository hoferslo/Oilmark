package com.storagecloud.oilmark.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.storagecloud.oilmark.Launch
import com.storagecloud.oilmark.R
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.Formats
import com.storagecloud.oilmark.data.LubeLoggerApi
import com.storagecloud.oilmark.data.Record
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.SettingsStore
import com.storagecloud.oilmark.data.Vehicle
import com.storagecloud.oilmark.widget.OilmarkWidget
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import androidx.glance.appwidget.updateAll

/*
 * Reminder notifications. A background job asks the server for every reminder a few times a
 * day; LubeLogger already works out how urgent each one is. A reminder is announced once when
 * it becomes "due soon", once more when "very soon", and once more when overdue - never twice
 * at the same level. The same job also refreshes the home-screen widget.
 */

/** Starts and triggers the background check. */
object ReminderSchedule {
    private const val PERIODIC = "reminder-check"
    private const val NOW = "reminder-check-now"
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Every 6 hours while the phone has a connection. Safe to call on every app start. */
    fun start(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
        )
    }

    /** One check right away, e.g. just after notifications were switched on. */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setConstraints(online).build(),
        )
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).settings.first()
        val server = settings.server ?: return Result.success()
        runCatching { OilmarkWidget().updateAll(applicationContext) }
        if (!settings.reminderNotifications || !ReminderNotifications.allowed(applicationContext)) {
            return Result.success()
        }
        try {
            val api = LubeLoggerApi(server)
            ReminderNotifications.show(
                applicationContext,
                reminders = api.allRecords(RecordType.Reminder),
                vehicles = api.vehicles().map { it.vehicle },
                formats = Formats(settings.units, settings.currency),
            )
        } catch (_: ApiException) {
            // Server unreachable: the next scheduled check tries again.
        }
        return Result.success()
    }
}

object ReminderNotifications {
    private const val CHANNEL = "reminders"

    /** Whether Android lets the app show notifications (permission granted and not blocked). */
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Notifies about reminders that got more urgent since the last check. */
    @SuppressLint("MissingPermission") // only called after allowed()
    fun show(context: Context, reminders: List<Record>, vehicles: List<Vehicle>, formats: Formats) {
        createChannel(context)
        val seen = context.getSharedPreferences("reminder_notifications", Context.MODE_PRIVATE)
        val manager = NotificationManagerCompat.from(context)
        val current = mutableSetOf<String>()

        for (reminder in reminders) {
            val key = "${reminder.vehicleId}:${reminder.id}"
            current += key
            val level = level(reminder.urgency)
            if (level == 0) {
                // Not due (any more): forget it, so it is announced again next time it comes due.
                seen.edit { remove(key) }
                continue
            }
            if (level <= seen.getInt(key, 0)) continue
            seen.edit { putInt(key, level) }

            val vehicle = vehicles.firstOrNull { it.id == reminder.vehicleId }
            val launch = Launch(Launch.Target.Reminders, reminder.vehicleId, vehicle?.isElectric == true)
            val tap = PendingIntent.getActivity(
                context, key.hashCode(), launch.intent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${reminder.description.orEmpty()} ${phrase(level)}")
                .setContentText(listOfNotNull(vehicle?.name, due(reminder, formats)).joinToString(" · "))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            manager.notify(key.hashCode(), notification)
        }
        // Reminders deleted or done on the server: nothing to remember about them.
        seen.edit { seen.all.keys.filter { it !in current }.forEach(::remove) }
    }

    /** 0 = not due yet, 1 = due soon, 2 = very soon, 3 = overdue. */
    private fun level(urgency: String?): Int = when (urgency) {
        "Urgent" -> 1
        "VeryUrgent" -> 2
        "PastDue" -> 3
        else -> 0
    }

    private fun phrase(level: Int) = when (level) {
        3 -> "is overdue"
        2 -> "is due very soon"
        else -> "is due soon"
    }

    /** "Due Nov 25, 2026 · at 180,000 km", whichever applies to how the reminder is set. */
    private fun due(reminder: Record, formats: Formats): String? = listOfNotNull(
        reminder.dueDate?.takeIf { reminder.metric != "Odometer" }?.let { "Due ${formats.date(it)}" },
        reminder.dueOdometer?.takeIf { it > 0 && reminder.metric != "Date" }?.let { "at ${formats.distance(it)}" },
    ).joinToString(" · ").ifEmpty { null }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a service or other reminder comes due"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
