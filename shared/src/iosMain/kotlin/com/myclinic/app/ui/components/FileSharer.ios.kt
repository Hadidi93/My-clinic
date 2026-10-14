package com.myclinic.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.myclinic.app.data.export.ExportedFile
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberFileSharer(): (ExportedFile) -> Unit = remember {
    { file ->
        val url = NSURL.fileURLWithPath(file.path)
        val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(sheet, animated = true, completion = null)
    }
}
