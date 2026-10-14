package com.myclinic.app.ui.files

import androidx.compose.runtime.Composable

/**
 * Opens the phone's photo library. [onPicked] receives a reader that returns
 * the chosen photo as a JPEG of at most [maxDimension] pixels (re-encoded,
 * so GPS and camera details are removed). Returns the function that opens the picker.
 */
@Composable
expect fun rememberPhotoPicker(maxDimension: Int, quality: Int, onPicked: (read: suspend () -> ByteArray) -> Unit): () -> Unit
