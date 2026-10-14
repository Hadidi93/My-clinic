package com.myclinic.app.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.myclinic.app.data.export.ExportedFile
import java.io.File

@Composable
actual fun rememberFileSharer(): (ExportedFile) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(file.path))
            val send = Intent(Intent.ACTION_SEND)
                .setType(file.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TITLE, file.title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, file.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
}
