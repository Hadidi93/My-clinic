package com.myclinic.app.data.security

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * The phone's protected key store. On iPhone this is the Keychain, written
 * in Swift (iosApp/KeychainStore.swift) with "this device only, after first
 * unlock" protection, so items never leave the phone or go into backups.
 */
interface SecureStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun delete(key: String)
}

/** Keeps the login (Supabase session) in the Keychain. */
class KeychainSessionManager(private val store: SecureStore) : SessionManager {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        store.write(KEY, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession? {
        val stored = store.read(KEY) ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), stored) }.getOrElse {
            store.delete(KEY)
            null
        }
    }

    override suspend fun deleteSession() = store.delete(KEY)

    private companion object {
        const val KEY = "supabase_session"
    }
}
