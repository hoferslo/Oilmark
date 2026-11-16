package com.storagecloud.oilmark.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.Attachment
import com.storagecloud.oilmark.ui.common.LocalApi
import com.storagecloud.oilmark.ui.common.LocalImages
import com.storagecloud.oilmark.ui.common.readPhoto
import com.storagecloud.oilmark.ui.common.rememberPhotoSource
import kotlinx.coroutines.launch

/**
 * The attachments of a record: photo thumbnails (tap to view full screen), other files as chips
 * (tap to open in the browser), and buttons to add photos from the camera or the gallery.
 *
 * New photos are uploaded right away and handed to [onChange]; they become part of the record
 * when the record is saved. Thumbnails only load once this is on screen, at thumbnail size.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Attachments(files: List<Attachment>, onChange: (List<Attachment>) -> Unit) {
    val api = LocalApi.current
    val context = LocalContext.current
    val browser = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // The latest values, for the upload that finishes later.
    val current by rememberUpdatedState(files)
    val change by rememberUpdatedState(onChange)
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }

    val photos = rememberPhotoSource(multiple = true) { uris ->
        uploading = true
        error = null
        scope.launch {
            try {
                val uploaded = api.upload(uris.map { readPhoto(context, it) })
                change(current + uploaded)
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't read that photo"
            }
            uploading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.AttachFile, contentDescription = null,
                Modifier.padding(end = 8.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Photos & files",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (files.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (file in files) {
                    if (file.isImage) {
                        Thumbnail(file, onOpen = { viewing = file }, onRemove = { onChange(files - file) })
                    } else {
                        InputChip(
                            selected = false,
                            onClick = { browser.openUri(api.fileUrl(file.location)) },
                            label = { Text(file.name) },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close, contentDescription = "Remove",
                                    Modifier.size(18.dp).clickable { onChange(files - file) },
                                )
                            },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = photos.takePhoto, enabled = !uploading, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, Modifier.padding(end = 8.dp).size(18.dp))
                Text("Take photo")
            }
            OutlinedButton(onClick = photos.choosePhotos, enabled = !uploading, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, Modifier.padding(end = 8.dp).size(18.dp))
                Text("Gallery")
            }
            if (uploading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    viewing?.let { PhotoViewer(it, onClose = { viewing = null }) }
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

/** True for photos (judged by file extension); these can be shown as pictures. */
val Attachment.isImage: Boolean
    get() = location.substringAfterLast('.', "").lowercase() in imageExtensions

@Composable
private fun Thumbnail(file: Attachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box(Modifier.size(96.dp)) {
        AsyncImage(
            model = LocalApi.current.fileUrl(file.location),
            contentDescription = file.name,
            imageLoader = LocalImages.current,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onOpen),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove", Modifier.size(16.dp), tint = Color.White)
        }
    }
}

/** Full-screen photo. Pinch to zoom, drag to pan, tap the cross to close. */
@Composable
private fun PhotoViewer(file: Attachment, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, drag, pinch, _ ->
                        zoom = (zoom * pinch).coerceIn(1f, 6f)
                        pan = if (zoom == 1f) Offset.Zero else pan + drag
                    }
                },
        ) {
            AsyncImage(
                model = LocalApi.current.fileUrl(file.location),
                contentDescription = file.name,
                imageLoader = LocalImages.current,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                file.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(16.dp),
            )
        }
    }
}
