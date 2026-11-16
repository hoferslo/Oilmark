package com.storagecloud.oilmark

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A screen to open straight away when the app starts from outside: the home-screen widget's
 * buttons or a reminder notification. Travels as extras on the intent that starts MainActivity.
 */
data class Launch(val target: Target, val vehicleId: Int, val electric: Boolean) {

    enum class Target { Vehicle, FuelUp, Odometer, Reminders }

    /** Starts the app fresh on this screen. */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        // A different address per target/vehicle, so Android keeps each widget button's and
        // notification's tap separate instead of reusing the first one.
        data = Uri.parse("oilmark://open/${target.name}/$vehicleId")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(TARGET, target.name)
        putExtra(VEHICLE, vehicleId)
        putExtra(ELECTRIC, electric)
    }

    companion object {
        private const val TARGET = "launch_target"
        private const val VEHICLE = "launch_vehicle"
        private const val ELECTRIC = "launch_electric"

        /** The screen [intent] asks for, or null for a normal start. */
        fun from(intent: Intent?): Launch? {
            val target = Target.entries.firstOrNull { it.name == intent?.getStringExtra(TARGET) } ?: return null
            val vehicleId = intent?.getIntExtra(VEHICLE, 0)?.takeIf { it > 0 } ?: return null
            return Launch(target, vehicleId, intent.getBooleanExtra(ELECTRIC, false))
        }
    }
}
