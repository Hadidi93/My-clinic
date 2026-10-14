package com.myclinic.app.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPhotoPicker(maxDimension: Int, quality: Int, onPicked: (read: suspend () -> ByteArray) -> Unit): () -> Unit {
    val picker = remember(maxDimension, quality) {
        PhotoLibraryPicker { image -> onPicked { image().toJpeg(maxDimension, quality / 100.0) } }
    }
    return picker::show
}
