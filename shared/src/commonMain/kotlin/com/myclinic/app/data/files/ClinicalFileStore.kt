package com.myclinic.app.data.files

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.uuid.Uuid

/**
 * Clinical files (lab reports, X-ray photos, wound photos) in the private
 * 'clinical-files' bucket, stored at <section>/<patient id>/<random name>.
 *
 * A file added while offline is kept on the phone, encrypted (see
 * [StagedFileVault]), until the sync uploads it. Downloaded files are only
 * held in memory, never cached on disk.
 */
class ClinicalFileStore(
    private val supabase: SupabaseClient,
    private val vault: StagedFileVault,
) {
    fun newPath(section: String, patientId: String, mimeType: String): String {
        val ext = if (mimeType == PickedFile.MIME_PDF) "pdf" else if (mimeType == "image/png") "png" else "jpg"
        return "$section/$patientId/${Uuid.random()}.$ext"
    }

    /** Keeps the file (encrypted) until it is uploaded. */
    suspend fun stage(path: String, bytes: ByteArray) = vault.put(path, bytes)

    fun isStaged(path: String): Boolean = vault.contains(path)

    /** Uploads a staged file, if there is one. Called by the sync before the attachment row is uploaded. */
    suspend fun uploadStaged(path: String) {
        val bytes = vault.get(path) ?: return
        try {
            upload(path, bytes, mimeTypeFor(path))
        } catch (e: Exception) {
            // Uploaded earlier, but the reply was lost: the same file is already there.
            if (!e.isAlreadyExists()) throw e
        }
        vault.remove(path)
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
        vault.get(path) ?: supabase.storage.from(BUCKET).downloadAuthenticated(path)

    /** Deletes files waiting for upload (on sign-out). */
    fun clearStaged() = vault.clear()

    private companion object {
        const val BUCKET = "clinical-files"

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
