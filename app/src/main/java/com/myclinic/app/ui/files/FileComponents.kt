package com.myclinic.app.ui.files

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myclinic.app.R
import com.myclinic.app.ui.components.TouchTarget
import java.io.File
import java.util.UUID

/** The three ways to add a file: take a photo, choose a photo, or choose a PDF. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachFileButtons(
    onImage: (uri: Uri, fromCamera: Boolean) -> Unit,
    onPdf: (Uri) -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri?.let(Uri::parse)
        cameraUri = null
        if (uri != null) {
            if (ok) onImage(uri, true) else runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onImage(it, false) }
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPdf)
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {
                val uri = newCameraUri(context)
                cameraUri = uri.toString()
                takePhoto.launch(uri)
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.file_take_photo)) },
            leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        AssistChip(
            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = enabled,
            label = { Text(stringResource(R.string.file_choose_photo)) },
            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        AssistChip(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            enabled = enabled,
            label = { Text(stringResource(R.string.file_choose_pdf)) },
            leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

/** A temporary file for the camera app to write into. It is deleted once the photo is read. */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/** One file in a list. [onOpen] null = not openable yet (e.g. picked but not saved). */
data class FileItem(val key: String, val label: String, val isPdf: Boolean, val note: String? = null)

@Composable
fun FileList(items: List<FileItem>, onOpen: ((FileItem) -> Unit)?, onRemove: ((FileItem) -> Unit)? = null) {
    Column {
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                    .then(if (onOpen != null) Modifier.clickable { onOpen(item) } else Modifier)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (item.isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Image, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(item.label, style = MaterialTheme.typography.bodyLarge)
                    item.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                onRemove?.let {
                    IconButton(onClick = { it(item) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.file_remove))
                    }
                }
            }
        }
    }
}
