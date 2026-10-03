package com.myclinic.app.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.myclinic.app.BuildConfig
import com.myclinic.app.MainActivity
import com.myclinic.app.R
import com.myclinic.domain.consult.NotificationKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Push notifications through Firebase Cloud Messaging. Firebase is set up in
 * code from four public identifiers (see app/build.gradle.kts), so no
 * google-services file is needed in the repository. Without them push is off
 * and the app still shows notifications inside the app.
 *
 * A push carries only the kind of event; the text shown is generic and
 * translated here ("New consult request"), never patient details.
 */
@Singleton
class PushManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotBlank()

    private val _received = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Kinds of pushes received while the app is running (screens refresh their lists). */
    val received: SharedFlow<String> = _received.asSharedFlow()

    private val _openRequest = MutableStateFlow<String?>(null)
    /** The kind of a notification the user tapped, until a screen handles it. */
    val openRequest: StateFlow<String?> = _openRequest.asStateFlow()

    fun init() {
        if (!isConfigured || FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
        createChannel()
    }

    suspend fun currentToken(): String? {
        if (!isConfigured) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                cont.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    suspend fun deleteToken() {
        if (!isConfigured) return
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { cont.resume(Unit) }
        }
    }

    /** Called by [PushService] for each push. */
    fun onPush(kind: String) {
        _received.tryEmit(kind)
        show(kind)
    }

    /** Called by MainActivity when it was opened from a notification. */
    fun onNotificationTapped(kind: String?) {
        if (kind != null && kind in NotificationKind.ALL) _openRequest.value = kind
    }

    fun onOpenHandled() {
        _openRequest.value = null
    }

    private fun show(kind: String) {
        if (kind !in NotificationKind.ALL) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, kind)
        val pending = PendingIntent.getActivity(
            context, kind.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(
            when (kind) {
                NotificationKind.CONSULT_REQUEST -> R.string.push_consult_request
                NotificationKind.CONSULT_MESSAGE -> R.string.push_consult_message
                NotificationKind.REFERRAL_REQUEST -> R.string.push_referral_request
                NotificationKind.REFERRAL_RESPONSE -> R.string.push_referral_response
                else -> R.string.push_lab_request
            },
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        // One notification per kind: a newer one replaces the older.
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(kind.hashCode(), notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.push_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN = "com.myclinic.app.OPEN_NOTIFICATION"
        private const val CHANNEL_ID = "activity"
    }
}
