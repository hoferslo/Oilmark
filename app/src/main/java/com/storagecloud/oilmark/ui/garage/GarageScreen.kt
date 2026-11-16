package com.storagecloud.oilmark.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.VehicleInfo
import com.storagecloud.oilmark.ui.common.LoadContent
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.Message
import com.storagecloud.oilmark.ui.common.VehiclePhoto
import com.storagecloud.oilmark.ui.common.rememberLoader

/** Home screen: every vehicle on the server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    onOpenVehicle: (vehicleId: Int) -> Unit,
    onAddVehicle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCompare: () -> Unit,
) {
    val api = LocalApi.current
    val garage = rememberLoader { api.vehicles() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garage") },
                actions = {
                    IconButton(onClick = onOpenCompare) {
                        Icon(Icons.Outlined.CompareArrows, contentDescription = "Compare vehicles")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddVehicle,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add vehicle") },
            )
        },
    ) { padding ->
        LoadContent(garage, Modifier.padding(padding)) { vehicles ->
            if (vehicles.isEmpty()) {
                Message(
                    icon = Icons.Outlined.DirectionsCar,
                    title = "Your garage is empty",
                    text = "Add your first vehicle to start tracking fuel, service and reminders.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vehicles, key = { it.vehicle.id }) { info ->
                        VehicleCard(info, onClick = { onOpenVehicle(info.vehicle.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(info: VehicleInfo, onClick: () -> Unit) {
    val formats = LocalFormats.current
    val vehicle = info.vehicle
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VehiclePhoto(
                vehicle,
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        vehicle.licensePlate?.takeIf { it.isNotBlank() },
                        formats.distance(info.lastReportedOdometer).takeIf { info.lastReportedOdometer > 0 },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (info.dueReminderCount > 0) {
                Badge { Text("${info.dueReminderCount} due", Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
            }
        }
    }
}
