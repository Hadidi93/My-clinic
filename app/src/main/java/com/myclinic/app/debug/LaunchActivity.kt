package com.myclinic.app.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myclinic.app.MainActivity
import com.myclinic.app.ui.theme.MyClinicTheme

/**
 * The app's entry point from the home screen. Normally it just opens
 * MainActivity straight away. If the previous run of a TEST build crashed,
 * it shows the error first, with a Copy button.
 *
 * Deliberately has no dependency injection or database, so it still works
 * when the crash was caused by those.
 */
class LaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = CrashReporter.lastCrash(this)
        if (crash == null) {
            openApp()
            return
        }
        setContent {
            MyClinicTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("The app crashed last time", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Please tap Copy and paste the text to Claude. It contains no patient data.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { copy(crash) }) { Text("Copy") }
                            OutlinedButton(onClick = {
                                CrashReporter.clear(this@LaunchActivity)
                                openApp()
                            }) { Text("Try again") }
                        }
                        SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text(crash, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("My Clinic crash", text))
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
