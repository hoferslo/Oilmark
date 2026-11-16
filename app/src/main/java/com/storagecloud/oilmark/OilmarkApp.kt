package com.storagecloud.oilmark

import android.app.Application
import com.storagecloud.oilmark.data.SettingsStore

class OilmarkApp : Application() {
    /** The saved server address, login and unit preference. */
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
