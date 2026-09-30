package com.myclinic.app.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the Supabase login session encrypted on disk (by default the
 * library stores it as plain text). If the stored value can't be decrypted,
 * for example after the Keystore key was wiped, we treat it as "logged out".
 */
@Singleton
class EncryptedSessionManager @Inject constructor(
    @ApplicationContext context: Context,
) : SessionManager {

    private val prefs = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("myclinic_session_key")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val encrypted = cipher.encrypt(json.encodeToString(UserSession.serializer(), session))
        prefs.edit().putString(KEY, encrypted).apply()
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(KEY, null) ?: return@withContext null
        runCatching { json.decodeFromString(UserSession.serializer(), cipher.decrypt(stored)) }
            .getOrElse {
                prefs.edit().remove(KEY).apply()
                null
            }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "session"
    }
}
