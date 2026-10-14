package com.myclinic.app.ui.components

import androidx.compose.runtime.Composable
import com.myclinic.app.data.export.ExportedFile

/** Opens the phone's share sheet for an exported file. */
@Composable
expect fun rememberFileSharer(): (ExportedFile) -> Unit
