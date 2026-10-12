package com.myclinic.app.data.push

import org.koin.android.ext.android.inject
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.myclinic.app.data.notifications.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Receives pushes from Firebase. A push holds only {"kind", "notification_id"}. */
class PushService : FirebaseMessagingService() {

    private val push: PushManager by inject()
    private val notifications: NotificationRepository by inject()
    private val supabase: SupabaseClient by inject()

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
