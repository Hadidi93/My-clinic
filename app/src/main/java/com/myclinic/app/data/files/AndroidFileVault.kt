package com.myclinic.app.data.files

import android.content.Context
import android.util.Base64
import com.myclinic.app.data.security.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android's [StagedFileVault]: files in the app's private no-backup folder,
 * encrypted with AES-256-GCM. The key is random and itself protected by
 * Android Keystore. File names are a hash of the storage path.
 */
class AndroidFileVault(private val context: Context) : StagedFileVault {
    private val dir: File get() = File(context.noBackupFilesDir, "pending_files").apply { mkdirs() }
    private val random = SecureRandom()

    override suspend fun put(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
        val tmp = File(dir, fileName(path) + ".tmp")
        tmp.writeBytes(iv + cipher.doFinal(bytes))
        tmp.renameTo(File(dir, fileName(path)))
        Unit
    }

    override suspend fun get(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(dir, fileName(path))
        if (!file.exists()) return@withContext null
        val data = file.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES)) }
        cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    override fun contains(path: String): Boolean = File(dir, fileName(path)).exists()

    override suspend fun remove(path: String) {
        withContext(Dispatchers.IO) { File(dir, fileName(path)).delete() }
    }

    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Never put the storage path (it contains the patient id) in a file name. */
    private fun fileName(path: String): String =
        MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }

    @Volatile private var cachedKey: SecretKeySpec? = null

    private fun key(): SecretKeySpec = cachedKey ?: synchronized(this) {
        cachedKey ?: run {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val wrapper = KeystoreCipher(KEYSTORE_ALIAS)
            val existing = prefs.getString(PREF_KEY, null)
                ?.let { runCatching { Base64.decode(wrapper.decrypt(it), Base64.NO_WRAP) }.getOrNull() }
            val bytes = existing ?: ByteArray(32).also { b ->
                random.nextBytes(b)
                // A new key means older staged files can't be read any more: remove them.
                clear()
                prefs.edit().putString(PREF_KEY, wrapper.encrypt(Base64.encodeToString(b, Base64.NO_WRAP))).commit()
            }
            SecretKeySpec(bytes, "AES").also { cachedKey = it }
        }
    }

    private companion object {
        const val PREFS = "clinical_files"
        const val PREF_KEY = "file_key"
        const val KEYSTORE_ALIAS = "myclinic_files_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
