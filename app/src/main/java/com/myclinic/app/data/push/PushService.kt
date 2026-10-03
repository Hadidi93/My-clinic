package com.myclinic.app.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.myclinic.app.data.notifications.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives pushes from Firebase. A push holds only {"kind", "notification_id"}. */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {

    @Inject lateinit var push: PushManager
    @Inject lateinit var notifications: NotificationRepository
    @Inject lateinit var supabase: SupabaseClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val kind = message.data["kind"] ?: return
        push.onPush(kind)
    }

    /** Firebase gave this phone a new push address: tell the server, if someone is signed in. */
    override fun onNewToken(token: String) {
        if (supabase.auth.currentUserOrNull() != null) {
            scope.launch { notifications.registerDevice() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
