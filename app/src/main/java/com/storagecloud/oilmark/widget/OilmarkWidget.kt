package com.storagecloud.oilmark.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.storagecloud.oilmark.Launch
import com.storagecloud.oilmark.MainActivity
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.Formats
import com.storagecloud.oilmark.data.LubeLoggerApi
import com.storagecloud.oilmark.data.Record
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.SettingsStore
import com.storagecloud.oilmark.data.Stats
import kotlinx.coroutines.flow.first

/*
 * Home-screen widget: the starred vehicle (or the first one) with its odometer, the last
 * tank's consumption and the next reminder, plus buttons to add a fuel-up or odometer reading.
 * It refreshes when the app is left, after each background reminder check, and every 3 hours.
 */

class OilmarkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OilmarkWidget()
}

class OilmarkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { GlanceTheme { Content(context, state) } }
    }
}

private sealed interface WidgetState {
    data object NotConnected : WidgetState
    data class Failed(val message: String) : WidgetState
    data class Ready(
        val vehicleId: Int,
        val electric: Boolean,
        val name: String,
        val odometer: String,
        val consumption: String?,
        val reminder: String?,
        val reminderUrgent: Boolean,
    ) : WidgetState
}

private suspend fun load(context: Context): WidgetState {
    val settings = SettingsStore(context).settings.first()
    val server = settings.server ?: return WidgetState.NotConnected
    return try {
        val api = LubeLoggerApi(server)
        val vehicles = api.vehicles()
        val info = vehicles.firstOrNull { it.vehicle.id == settings.defaultVehicleId }
            ?: vehicles.firstOrNull()
            ?: return WidgetState.Failed("No vehicles yet")
        val formats = Formats(settings.units, settings.currency)
        val electric = info.vehicle.isElectric
        val tank = Stats.fuel(api.records(RecordType.Fuel, info.vehicle.id)).lastTank
        val reminder = info.nextReminder
        WidgetState.Ready(
            vehicleId = info.vehicle.id,
            electric = electric,
            name = info.vehicle.name,
            odometer = formats.distance(info.lastReportedOdometer),
            consumption = tank?.let { formats.consumption(it.distance, it.fuel, electric) },
            reminder = reminder?.let { "${it.description.orEmpty()} · ${whenDue(it, formats)}" },
            reminderUrgent = reminder?.urgency != null && reminder.urgency != "NotUrgent",
        )
    } catch (e: ApiException) {
        WidgetState.Failed(e.message.orEmpty())
    }
}

/** "in 8 days", "3 days overdue", "in 1,200 km" - by date when the reminder uses one. */
private fun whenDue(reminder: Record, formats: Formats): String {
    val days = reminder.dueDays
    val distance = reminder.dueDistance
    return when {
        reminder.metric != "Odometer" && days != null -> when {
            days < 0 -> "${-days} days overdue"
            days == 0 -> "today"
            days == 1 -> "tomorrow"
            else -> "in $days days"
        }
        distance != null -> if (distance < 0) "${formats.distance(-distance)} overdue" else "in ${formats.distance(distance)}"
        else -> "due"
    }
}

@Composable
private fun Content(context: Context, state: WidgetState) {
    val openApp: Action = actionStartActivity(
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    val title = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface)
    val body = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)

    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        when (state) {
            WidgetState.NotConnected -> {
                Text("Oilmark", style = title, modifier = GlanceModifier.clickable(openApp))
                Text("Open the app to connect to your LubeLogger server.", style = body)
            }
            is WidgetState.Failed -> {
                Text("Oilmark", style = title, modifier = GlanceModifier.clickable(openApp))
                Text(state.message, style = body, maxLines = 2)
            }
            is WidgetState.Ready -> {
                fun open(target: Launch.Target) =
                    actionStartActivity(Launch(target, state.vehicleId, state.electric).intent(context))

                Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open(Launch.Target.Vehicle))) {
                    Text(state.name, style = title, maxLines = 1)
                    Text(listOfNotNull(state.odometer, state.consumption).joinToString("  ·  "), style = body, maxLines = 1)
                    if (state.reminder != null) {
                        Text(
                            state.reminder,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = if (state.reminderUrgent) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Row(GlanceModifier.fillMaxWidth()) {
                    Button(
                        if (state.electric) "+ Charge" else "+ Fuel-up",
                        onClick = open(Launch.Target.FuelUp),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Button("+ Odometer", onClick = open(Launch.Target.Odometer), modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}
