package com.myclinic.app.ui.files

import com.myclinic.shared.resources.*
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.ui.components.TouchTarget

/**
 * The three ways to add a file: take a photo, choose a photo, or choose a PDF.
 * [onFile] receives a reader: the screen's view model runs it (it may take a
 * moment to shrink a photo) and keeps the [PickedFile] it returns.
 */
@Composable
expect fun AttachFileButtons(onFile: (read: suspend () -> PickedFile) -> Unit, enabled: Boolean = true)

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
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.file_remove))
                    }
                }
            }
        }
    }
}
