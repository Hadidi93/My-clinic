package com.myclinic.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.ui.root.MyClinicRoot
import com.myclinic.app.ui.theme.MyClinicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The app's only Activity. Every screen is a Compose function shown inside it.
 * It extends AppCompatActivity so the in-app language switch (English/Arabic)
 * works on all supported Android versions.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after rotation the same link was already handled.
        if (savedInstanceState == null) authRepository.handleDeepLink(intent)
        setContent {
            MyClinicTheme {
                MyClinicRoot()
            }
        }
    }

    /** Called when an email link (verify / reset password) opens the already-running app. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authRepository.handleDeepLink(intent)
    }
}
