package com.storagecloud.oilmark.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.storagecloud.oilmark.data.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Two ways to get a photo. Call either function from a button's onClick. */
class PhotoSource(val takePhoto: () -> Unit, val choosePhotos: () -> Unit)

/**
 * Hooks up the camera and the phone's photo picker. [onPhotos] gets the chosen photos
 * (always one from the camera; one or several from the picker depending on [multiple]).
 * No permissions are needed for either.
 */
@Composable
fun rememberPhotoSource(multiple: Boolean, onPhotos: (List<Uri>) -> Unit): PhotoSource {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onPhotos)
    // Saved so the result still arrives if Android restarts the app while the camera is open.
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraFile
        if (taken && file != null) deliver(listOf(Uri.parse(file)))
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(listOf(uri))
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) deliver(uris)
    }

    return PhotoSource(
        takePhoto = {
            val file = File(context.cacheDir, "photos/${System.currentTimeMillis()}.jpg")
            file.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
            cameraFile = uri.toString()
            camera.launch(uri)
        },
        choosePhotos = {
            val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (multiple) pickMany.launch(imagesOnly) else pickOne.launch(imagesOnly)
        },
    )
}

/** "Take photo" / "Choose from gallery" chooser. */
@Composable
fun PhotoSourceDialog(title: String, source: PhotoSource, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                    headlineContent = { Text("Take photo") },
                    colors = colors,
                    modifier = Modifier.clickable { onDismiss(); source.takePhoto() },
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                    headlineContent = { Text("Choose from gallery") },
                    colors = colors,
                    modifier = Modifier.clickable { onDismiss(); source.choosePhotos() },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Reads a photo and shrinks it to at most [maxSize] pixels on its long side as a JPEG, so
 * uploads are quick and don't fill the server with 10 MB originals.
 */
suspend fun readPhoto(context: Context, uri: Uri, maxSize: Int = 2000): Upload = withContext(Dispatchers.IO) {
    val name = "photo-${System.currentTimeMillis()}.jpg"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder also turns the picture the right way up.
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > maxSize) {
                val scale = maxSize.toFloat() / longSide
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        bitmap.recycle()
        Upload(name, bytes)
    } else {
        // Android 8: no ImageDecoder, send the original.
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        Upload(name, bytes, context.contentResolver.getType(uri) ?: "image/jpeg")
    }
}
