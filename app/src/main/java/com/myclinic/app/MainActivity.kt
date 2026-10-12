package com.myclinic.app

import org.koin.android.ext.android.inject
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.data.push.PushManager
import com.myclinic.app.security.AppLockManager
import com.myclinic.app.ui.root.MyClinicRoot
import com.myclinic.app.ui.theme.MyClinicTheme

/**
 * The app's only Activity. Every screen is a Compose function shown inside it.
 * It extends AppCompatActivity so the in-app language switch (English/Arabic)
 * works on all supported Android versions.
 */
class MainActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()
    private val push: PushManager by inject()
    private val appLock: AppLockManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after rotation the same link was already handled.
        if (savedInstanceState == null) {
            authRepository.handleDeepLink(intent?.dataString)
            push.onNotificationTapped(intent?.getStringExtra(PushManager.EXTRA_OPEN))
        }
        setContent {
            MyClinicTheme {
                MyClinicRoot()
            }
        }
        // Ignore touches while another app draws over this one (tap-jacking protection).
        window.decorView.filterTouchesWhenObscured = true
    }

    /** Every touch counts as "in use" for the 5-minute app lock. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        appLock.onUserInteraction()
    }

    /** Called when an email link (verify / reset password) opens the already-running app. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authRepository.handleDeepLink(intent.dataString)
        push.onNotificationTapped(intent.getStringExtra(PushManager.EXTRA_OPEN))
    }
}
