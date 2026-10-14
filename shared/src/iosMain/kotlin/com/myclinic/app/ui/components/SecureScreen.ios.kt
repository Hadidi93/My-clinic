package com.myclinic.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenCapturedDidChangeNotification

@Composable
actual fun SecureScreen(content: @Composable () -> Unit) {
    var captured by remember { mutableStateOf(UIScreen.mainScreen.isCaptured()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UIScreenCapturedDidChangeNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> captured = UIScreen.mainScreen.isCaptured() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    if (captured) Box(Modifier.fillMaxSize()) else content()
}
