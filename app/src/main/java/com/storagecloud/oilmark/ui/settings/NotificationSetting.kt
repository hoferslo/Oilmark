package com.storagecloud.oilmark.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.storagecloud.oilmark.notify.ReminderNotifications
import com.storagecloud.oilmark.notify.ReminderSchedule
import com.storagecloud.oilmark.ui.common.LocalSettings
import com.storagecloud.oilmark.ui.common.LocalSettingsStore
import com.storagecloud.oilmark.ui.common.ToggleInput
import kotlinx.coroutines.launch

/** On/off for reminder notifications, and a way out when Android blocks them. */
@Composable
fun ReminderNotificationSetting() {
    val settings = LocalSettings.current
    val store = LocalSettingsStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(ReminderNotifications.allowed(context)) }
    // Re-checked on return from Android's settings.
    LifecycleResumeEffect(Unit) {
        allowed = ReminderNotifications.allowed(context)
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) ReminderSchedule.checkNow(context)
    }

    ToggleInput(
        "Notify me when reminders come due", settings.reminderNotifications,
        onChange = { on ->
            scope.launch { store.setReminderNotifications(on) }
            if (on && !allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (on) {
                ReminderSchedule.checkNow(context)
            }
        },
        icon = Icons.Outlined.NotificationsActive,
    )
    Text(
        "Checks your reminders every few hours. Each one is announced when it becomes due soon, " +
            "and again if it becomes overdue.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (settings.reminderNotifications && !allowed) {
        Text(
            "Android is blocking notifications from Oilmark.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow in Android settings") }
    }
}
