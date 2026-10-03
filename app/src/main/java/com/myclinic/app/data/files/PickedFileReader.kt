package com.myclinic.app.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.myclinic.app.data.media.ImageCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FileTooLargeException : Exception("File is larger than 15 MB")

/** Turns a photo or PDF the user picked (or took) into a [PickedFile]. */
class PickedFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressor: ImageCompressor,
) {
    /** Photos are resized to at most 2560 px and re-encoded, which also removes GPS and camera details. */
    suspend fun image(uri: Uri, deleteOriginal: Boolean = false): PickedFile {
        try {
            return PickedFile(imageCompressor.toJpeg(uri, maxDimension = 2560, quality = 88), PickedFile.MIME_JPEG, null)
        } finally {
            // A camera photo is first written to a temporary file of ours; never leave it behind.
            if (deleteOriginal) withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(uri, null, null) } }
        }
    }

    suspend fun pdf(uri: Uri): PickedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                buffer.write(chunk, 0, n)
                if (buffer.size() > PickedFile.MAX_BYTES) throw FileTooLargeException()
            }
            buffer.toByteArray()
        } ?: throw java.io.IOException("Cannot read file")
        PickedFile(bytes, PickedFile.MIME_PDF, name?.take(200))
    }
}
