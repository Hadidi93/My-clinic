package com.myclinic.app.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.myclinic.app.data.media.ImageCompressor
import org.koin.compose.koinInject

@Composable
actual fun rememberPhotoPicker(maxDimension: Int, quality: Int, onPicked: (read: suspend () -> ByteArray) -> Unit): () -> Unit {
    val compressor = koinInject<ImageCompressor>()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked { compressor.toJpeg(it, maxDimension = maxDimension, quality = quality) } }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
