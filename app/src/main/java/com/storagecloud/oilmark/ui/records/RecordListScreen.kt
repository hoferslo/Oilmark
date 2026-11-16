package com.storagecloud.oilmark.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.RecordType
import com.storagecloud.oilmark.ui.common.BackTopBar
import com.storagecloud.oilmark.ui.common.LoadContent
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalFormats
import com.storagecloud.oilmark.ui.common.LocalImages
import com.storagecloud.oilmark.ui.common.Message
import com.storagecloud.oilmark.ui.common.rememberLoader
import com.storagecloud.oilmark.ui.stats.StatsKind

/** How big a supply's photo is in the list. Change this one number to resize it. */
private val SupplyPhotoSize = 88.dp

/** All records of one [type] for a vehicle. The same screen serves every record type. */
@Composable
fun RecordListScreen(
    type: RecordType,
    vehicleId: Int,
    electric: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (recordId: Int) -> Unit,
    onOpenStats: (StatsKind) -> Unit,
) {
    val api = LocalApi.current
    val formats = LocalFormats.current
    val loader = rememberLoader { type.sorted(api.records(type, vehicleId, formats.units)) }

    Scaffold(
        topBar = {
            BackTopBar(type.title, onBack) {
                // Fuel and odometer have analytics of their own.
                val stats = when (type) {
                    RecordType.Fuel -> StatsKind.Fuel
                    RecordType.Odometer -> StatsKind.Distance
                    else -> null
                }
                if (stats != null) {
                    IconButton(onClick = { onOpenStats(stats) }) {
                        Icon(Icons.Outlined.Insights, contentDescription = "Analytics")
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add ${type.noun}") },
            )
        },
    ) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { records ->
            if (records.isEmpty()) {
                Message(type.icon, "Nothing here yet", "Tap “Add ${type.noun}” to create the first one.")
                return@LoadContent
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                val total = records.sumOf { it.cost ?: 0.0 }
                if (total > 0) {
                    item {
                        Text(
                            "${records.size} · ${formats.money(total)} in total",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(records, key = { it.id }) { record ->
                    val row = type.row(record, formats, electric)
                    ListItem(
                        leadingContent = {
                            if (type == RecordType.Supply) {
                                // Supplies get a large picture, so you can tell parts apart at a glance.
                                val photo = record.files.firstOrNull { it.isImage }
                                Box(
                                    modifier = Modifier
                                        .size(SupplyPhotoSize)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (photo != null) {
                                        // Decoded at this size, not full resolution, so scrolling stays light.
                                        AsyncImage(
                                            model = api.fileUrl(photo.location),
                                            contentDescription = null,
                                            imageLoader = LocalImages.current,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            type.icon, contentDescription = null,
                                            Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            if (row.subtitle.isNotBlank()) {
                                Text(row.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        trailingContent = row.trailing?.let {
                            {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (row.alert) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        },
                        modifier = Modifier.clickable { onOpen(record.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
