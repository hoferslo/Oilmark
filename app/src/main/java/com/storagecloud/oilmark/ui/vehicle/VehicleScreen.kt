package com.storagecloud.oilmark.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.data.VehicleInfo
import com.storagecloud.oilmark.ui.common.BackTopBar
import com.storagecloud.oilmark.ui.common.ConfirmDeleteDialog
import com.storagecloud.oilmark.ui.common.Load
import com.storagecloud.oilmark.ui.common.LoadContent
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.LocalSettings
import com.storagecloud.oilmark.ui.common.LocalSettingsStore
import com.storagecloud.oilmark.ui.common.PhotoSourceDialog
import com.storagecloud.oilmark.ui.common.VehiclePhoto
import com.storagecloud.oilmark.ui.common.readPhoto
import com.storagecloud.oilmark.ui.common.rememberLoader
import com.storagecloud.oilmark.ui.common.rememberPhotoSource
import com.storagecloud.oilmark.ui.records.icon
import com.storagecloud.oilmark.ui.stats.StatsKind
import kotlinx.coroutines.launch

/** One vehicle: photo, key numbers, quick actions and a door into each kind of record. */
@Composable
fun VehicleScreen(
    vehicleId: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenRecords: (RecordType, electric: Boolean) -> Unit,
    onAddRecord: (RecordType, electric: Boolean) -> Unit,
    onOpenStats: (StatsKind) -> Unit,
    onOpenCompare: () -> Unit,
) {
    val api = LocalApi.current
    val settings = LocalSettings.current
    val store = LocalSettingsStore.current
    val context = LocalContext.current
    val loader = rememberLoader { api.vehicle(vehicleId) }
    val info = (loader.state as? Load.Ready)?.value
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingPhoto by remember { mutableStateOf(false) }
    var savingPhoto by remember { mutableStateOf(false) }
    val isDefault = settings.defaultVehicleId == vehicleId

    val photos = rememberPhotoSource(multiple = false) { uris ->
        savingPhoto = true
        scope.launch {
            try {
                api.setVehiclePhoto(vehicleId, readPhoto(context, uris.first()))
                loader.refresh()
            } catch (e: ApiException) {
                snackbar.showSnackbar(e.message.orEmpty())
            } catch (e: Exception) {
                snackbar.showSnackbar("Couldn't read that photo")
            }
            savingPhoto = false
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(info?.vehicle?.name.orEmpty(), onBack) {
                IconButton(onClick = {
                    scope.launch {
                        store.setDefaultVehicle(if (isDefault) null else vehicleId)
                        snackbar.showSnackbar(
                            if (isDefault) "The app opens the garage again" else "The app now opens straight to this vehicle",
                        )
                    }
                }) {
                    Icon(
                        if (isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isDefault) "Stop opening this vehicle at start" else "Open this vehicle at start",
                    )
                }
                if (info != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit vehicle") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete vehicle")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { vehicleInfo ->
            val electric = vehicleInfo.vehicle.isElectric
            VehicleOverview(
                info = vehicleInfo,
                shown = RecordType.entries - settings.hidden,
                savingPhoto = savingPhoto,
                onChangePhoto = { choosingPhoto = true },
                onOpenRecords = { onOpenRecords(it, electric) },
                onAddRecord = { onAddRecord(it, electric) },
                onOpenStats = onOpenStats,
                onOpenCompare = onOpenCompare,
            )
        }
    }

    if (choosingPhoto) {
        PhotoSourceDialog("Change photo", photos, onDismiss = { choosingPhoto = false })
    }
    if (confirmDelete && info != null) {
        ConfirmDeleteDialog(
            what = info.vehicle.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    try {
                        api.deleteVehicle(vehicleId)
                        if (isDefault) store.setDefaultVehicle(null)
                        onBack()
                    } catch (e: ApiException) {
                        snackbar.showSnackbar(e.message.orEmpty())
                    }
                }
            },
        )
    }
}

@Composable
private fun VehicleOverview(
    info: VehicleInfo,
    /** Record types the user hasn't hidden in settings. */
    shown: List<RecordType>,
    savingPhoto: Boolean,
    onChangePhoto: () -> Unit,
    onOpenRecords: (RecordType) -> Unit,
    onAddRecord: (RecordType) -> Unit,
    onOpenStats: (StatsKind) -> Unit,
    onOpenCompare: () -> Unit,
) {
    val formats = LocalFormats.current
    val vehicle = info.vehicle
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tap the photo to replace it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(enabled = !savingPhoto, onClickLabel = "Change photo", onClick = onChangePhoto),
        ) {
            VehiclePhoto(vehicle, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (savingPhoto) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }

        // Tap a number to see it as a chart.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(
                "Odometer", Icons.Outlined.Speed, formats.distance(info.lastReportedOdometer),
                onClick = { onOpenStats(StatsKind.Distance) }, Modifier.weight(1f),
            )
            Stat(
                "Total spent", Icons.Outlined.Payments, formats.money(info.totalCost),
                onClick = { onOpenStats(StatsKind.Spending) }, Modifier.weight(1f),
            )
        }

        val quick = listOf(RecordType.Fuel, RecordType.Odometer).filter { it in shown }
        if (quick.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (type in quick) {
                    val label = when {
                        type == RecordType.Odometer -> "Add odometer"
                        vehicle.isElectric -> "Add charge"
                        else -> "Add fuel-up"
                    }
                    // The two things done most often, so they get big, easy-to-hit buttons. The solid
                    // colour, the plus sign and "Add ..." all say: this creates a new entry.
                    Button(
                        onClick = { onAddRecord(type) },
                        modifier = Modifier.weight(1f).height(88.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(22.dp))
                                Icon(type.icon, contentDescription = null, Modifier.padding(start = 2.dp).size(28.dp))
                            }
                            Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (RecordType.Fuel in shown) {
            Card(onClick = { onOpenStats(StatsKind.Fuel) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Insights, contentDescription = null) },
                    headlineContent = { Text("Fuel analytics") },
                    supportingContent = { Text("Consumption, cost per distance, prices") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Card(onClick = onOpenCompare, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.CompareArrows, contentDescription = null) },
                headlineContent = { Text("Compare years & vehicles") },
                supportingContent = { Text("Cost per distance, consumption, year against year") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        if (info.dueReminderCount > 0 && RecordType.Reminder in shown) {
            Card(
                onClick = { onOpenRecords(RecordType.Reminder) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                    headlineContent = {
                        val count = info.dueReminderCount
                        Text(if (count == 1) "1 reminder needs attention" else "$count reminders need attention")
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = MaterialTheme.colorScheme.onErrorContainer,
                        leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }
        }

        if (shown.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                shown.forEachIndexed { index, type ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        leadingContent = { Icon(type.icon, contentDescription = null) },
                        headlineContent = { Text(type.title) },
                        supportingContent = info.summaryOf(type)?.let { { Text(it) } },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenRecords(type) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, icon: ImageVector, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, contentDescription = null,
                    Modifier.padding(end = 6.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text("See chart", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "3 · $191.10" for the kinds the server gives totals for; nothing for the rest. */
@Composable
private fun VehicleInfo.summaryOf(type: RecordType): String? {
    val formats = LocalFormats.current
    fun counted(count: Int, cost: Double) =
        if (count == 0) "Nothing yet" else "$count · ${formats.money(cost)}"
    return when (type) {
        RecordType.Fuel -> counted(gasRecordCount, gasRecordCost)
        RecordType.Service -> counted(serviceRecordCount, serviceRecordCost)
        RecordType.Repair -> counted(repairRecordCount, repairRecordCost)
        RecordType.Upgrade -> counted(upgradeRecordCount, upgradeRecordCost)
        RecordType.Tax -> counted(taxRecordCount, taxRecordCost)
        RecordType.Reminder -> if (reminderCount == 0) "Nothing yet" else "$reminderCount active"
        RecordType.Plan -> if (openPlanCount == 0) "Nothing planned" else "$openPlanCount open"
        else -> null
    }
}
