package com.myclinic.app.data.files

import android.content.Context
import android.util.Base64
import com.myclinic.app.data.security.KeystoreCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** A photo or PDF chosen by the user, ready to attach. Images are already re-encoded (no GPS/EXIF). */
class PickedFile(val bytes: ByteArray, val mimeType: String, val fileName: String?) {
    val isPdf: Boolean get() = mimeType == MIME_PDF

    companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PDF = "application/pdf"
        const val MAX_BYTES = 15 * 1024 * 1024 // same limit as the storage bucket
    }
}

/**
 * Clinical files (lab reports, X-ray photos, wound photos) in the private
 * 'clinical-files' bucket, stored at <section>/<patient id>/<random name>.
 *
 * A file added while offline is kept on the phone, encrypted (AES-256-GCM
 * with a random key that is itself protected by Android Keystore), until the
 * sync uploads it. Downloaded files are only held in memory, never cached on disk.
 */
@Singleton
class ClinicalFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) {
    private val dir: File get() = File(context.noBackupFilesDir, "pending_files").apply { mkdirs() }
    private val random = SecureRandom()

    fun newPath(section: String, patientId: String, mimeType: String): String {
        val ext = if (mimeType == PickedFile.MIME_PDF) "pdf" else if (mimeType == "image/png") "png" else "jpg"
        return "$section/$patientId/${UUID.randomUUID()}.$ext"
    }

    /** Keeps the file (encrypted) until it is uploaded. */
    suspend fun stage(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
        val tmp = File(dir, fileName(path) + ".tmp")
        tmp.writeBytes(iv + cipher.doFinal(bytes))
        tmp.renameTo(File(dir, fileName(path)))
    }

    fun isStaged(path: String): Boolean = File(dir, fileName(path)).exists()

    /** Uploads a staged file, if there is one. Called by the sync before the attachment row is uploaded. */
    suspend fun uploadStaged(path: String) {
        val bytes = readStaged(path) ?: return
        try {
            upload(path, bytes, mimeTypeFor(path))
        } catch (e: Exception) {
            // Uploaded earlier, but the reply was lost: the same file is already there.
            if (!e.isAlreadyExists()) throw e
        }
        withContext(Dispatchers.IO) { File(dir, fileName(path)).delete() }
    }

    /** Direct upload (online only), e.g. for lab staff submitting a result. */
    suspend fun upload(path: String, bytes: ByteArray, mimeType: String) {
        supabase.storage.from(BUCKET).upload(path, bytes) {
            upsert = false // files are never replaced (the bucket has no update permission)
            contentType = ContentType.parse(mimeType)
        }
    }

    /** The file's bytes: from the phone if not uploaded yet, otherwise downloaded (into memory only). */
    suspend fun load(path: String): ByteArray =
        readStaged(path) ?: supabase.storage.from(BUCKET).downloadAuthenticated(path)

    /** Deletes files waiting for upload (on sign-out). */
    fun clearStaged() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun readStaged(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(dir, fileName(path))
        if (!file.exists()) return@withContext null
        val data = file.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES)) }
        cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
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
                clearStaged()
                prefs.edit().putString(PREF_KEY, wrapper.encrypt(Base64.encodeToString(b, Base64.NO_WRAP))).commit()
            }
            SecretKeySpec(bytes, "AES").also { cachedKey = it }
        }
    }

    private companion object {
        const val BUCKET = "clinical-files"
        const val PREFS = "clinical_files"
        const val PREF_KEY = "file_key"
        const val KEYSTORE_ALIAS = "myclinic_files_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "pdf" -> PickedFile.MIME_PDF
            "png" -> "image/png"
            else -> PickedFile.MIME_JPEG
        }

        fun Exception.isAlreadyExists(): Boolean = message.orEmpty().let {
            it.contains("already exists", ignoreCase = true) || it.contains("Duplicate", ignoreCase = true) || it.contains("409")
        }
    }
}
