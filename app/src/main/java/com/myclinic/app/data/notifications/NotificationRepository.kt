package com.myclinic.app.data.notifications

import com.myclinic.app.data.push.PushManager
import com.myclinic.domain.consult.AppNotification
import com.myclinic.domain.record.RecordJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The in-app notification list (bell icon). Rows only say what happened
 * ("new consult request") and which consult/referral/request it is about.
 */
interface NotificationRepository {
    val notifications: StateFlow<List<AppNotification>>
    val unreadCount: StateFlow<Int>
    suspend fun refresh(): Result<Unit>
    suspend fun markRead(ids: Collection<String>)
    suspend fun markAllRead()

    /** Registers this phone for push notifications (after sign-in). */
    suspend fun registerDevice()
    /** Stops push to this phone (before sign-out). */
    suspend fun unregisterDevice()
    fun clear()

    /** Kinds of pushes received while the app runs (lists refresh themselves). */
    val pushReceived: SharedFlow<String>
    /** The kind of notification the user tapped to open the app, until handled. */
    val openRequest: StateFlow<String?>
    fun onOpenHandled()
}

@Singleton
class SupabaseNotificationRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val push: PushManager,
) : NotificationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val pushReceived: SharedFlow<String> = push.received
    override val openRequest: StateFlow<String?> = push.openRequest
    override fun onOpenHandled() = push.onOpenHandled()

    init {
        // A push arrived while the app is open: bring the bell list up to date.
        scope.launch { push.received.collect { refresh() } }
    }

    private val list = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = list.asStateFlow()
    private val unread = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = unread.asStateFlow()

    private fun publish(items: List<AppNotification>) {
        list.value = items
        unread.value = items.count { !it.isRead }
    }

    override suspend fun refresh(): Result<Unit> = call {
        val result = supabase.from(TABLE).select {
            order("created_at", Order.DESCENDING)
            limit(100)
        }
        publish(RecordJson.decodeFromString(ListSerializer(AppNotification.serializer()), result.data))
    }

    override suspend fun markRead(ids: Collection<String>) {
        val unreadIds = ids.filter { id -> list.value.any { it.id == id && !it.isRead } }
        if (unreadIds.isEmpty()) return
        val now = Instant.now().toString()
        publish(list.value.map { if (it.id in unreadIds) it.copy(readAt = now) else it })
        call {
            supabase.from(TABLE).update(buildJsonObject { put("read_at", now) }) {
                filter { isIn("id", unreadIds) }
            }
        }
    }

    override suspend fun markAllRead() {
        val now = Instant.now().toString()
        publish(list.value.map { if (it.isRead) it else it.copy(readAt = now) })
        call { supabase.postgrest.rpc("mark_all_notifications_read") }
    }

    override suspend fun registerDevice() {
        val token = push.currentToken() ?: return
        call { supabase.postgrest.rpc("register_device", buildJsonObject { put("p_token", token) }) }
    }

    override suspend fun unregisterDevice() {
        val token = push.currentToken() ?: return
        call { supabase.postgrest.rpc("unregister_device", buildJsonObject { put("p_token", token) }) }
        push.deleteToken() // a new address is made for the next person who signs in
    }

    override fun clear() = publish(emptyList())

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val TABLE = "notifications"
    }
}
