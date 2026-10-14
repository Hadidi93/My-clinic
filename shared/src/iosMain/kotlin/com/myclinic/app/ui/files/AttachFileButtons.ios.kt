package com.myclinic.app.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myclinic.app.data.files.PickedFile
import com.myclinic.shared.resources.Res
import com.myclinic.shared.resources.file_choose_pdf
import com.myclinic.shared.resources.file_choose_photo
import com.myclinic.shared.resources.file_take_photo
import org.jetbrains.compose.resources.stringResource

/** Photos are resized to at most 2560 px and re-encoded, which also removes GPS and camera details. */
private const val MAX_PHOTO_PX = 2560

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun AttachFileButtons(onFile: (read: suspend () -> PickedFile) -> Unit, enabled: Boolean) {
    val asPhoto: (suspend () -> platform.UIKit.UIImage) -> Unit = { image ->
        onFile { PickedFile(image().toJpeg(MAX_PHOTO_PX, 0.88), PickedFile.MIME_JPEG, null) }
    }
    // The pickers must stay alive while they are on screen.
    val camera = remember { CameraPicker(asPhoto) }
    val library = remember { PhotoLibraryPicker(asPhoto) }
    val pdf = remember { PdfPicker(onFile) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (camera.available) {
            AssistChip(
                onClick = camera::show, enabled = enabled,
                label = { Text(stringResource(Res.string.file_take_photo)) },
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        AssistChip(
            onClick = library::show, enabled = enabled,
            label = { Text(stringResource(Res.string.file_choose_photo)) },
            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        AssistChip(
            onClick = pdf::show, enabled = enabled,
            label = { Text(stringResource(Res.string.file_choose_pdf)) },
            leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
