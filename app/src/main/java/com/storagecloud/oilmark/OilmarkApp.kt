package com.storagecloud.oilmark

import android.app.Application
import com.storagecloud.oilmark.data.SettingsStore
import com.storagecloud.oilmark.notify.ReminderSchedule

class OilmarkApp : Application() {
    /** The saved server address, login and unit preference. */
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        // Reminder notifications and widget refreshes; the job itself checks whether they are on.
        ReminderSchedule.start(this)
    }
}
