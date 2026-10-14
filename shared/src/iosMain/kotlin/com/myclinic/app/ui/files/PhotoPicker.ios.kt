package com.myclinic.app.ui.files

import androidx.compose.runtime.Composable

// The iPhone photo library picker is added with the iPhone app shell.
@Composable
actual fun rememberPhotoPicker(maxDimension: Int, quality: Int, onPicked: (read: suspend () -> ByteArray) -> Unit): () -> Unit = {}
