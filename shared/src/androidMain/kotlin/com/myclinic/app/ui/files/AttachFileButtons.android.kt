package com.myclinic.app.ui.files

import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.data.files.PickedFileReader
import org.koin.compose.koinInject

import com.myclinic.shared.resources.*
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myclinic.app.ui.components.TouchTarget
import java.io.File
import java.util.UUID

/** The three ways to add a file: take a photo, choose a photo, or choose a PDF. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun AttachFileButtons(onFile: (read: suspend () -> PickedFile) -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    val reader = koinInject<PickedFileReader>()
    val onImage: (Uri, Boolean) -> Unit = { uri, fromCamera -> onFile { reader.image(uri, deleteOriginal = fromCamera) } }
    val onPdf: (Uri) -> Unit = { uri -> onFile { reader.pdf(uri) } }
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
            label = { Text(stringResource(Res.string.file_take_photo)) },
            leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        AssistChip(
            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = enabled,
            label = { Text(stringResource(Res.string.file_choose_photo)) },
            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        AssistChip(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            enabled = enabled,
            label = { Text(stringResource(Res.string.file_choose_pdf)) },
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

