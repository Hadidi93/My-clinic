package com.myclinic.app.ui.files

import androidx.compose.runtime.Composable
import com.myclinic.app.data.files.PickedFile

// The iPhone camera / photo library / Files picker is added with the iPhone app shell.
@Composable
actual fun AttachFileButtons(onFile: (read: suspend () -> PickedFile) -> Unit, enabled: Boolean) = Unit
