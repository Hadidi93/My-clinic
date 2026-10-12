package com.myclinic.app.data.files

/** A photo or PDF chosen by the user, ready to attach. Images are already re-encoded (no GPS/EXIF). */
class PickedFile(val bytes: ByteArray, val mimeType: String, val fileName: String?) {
    val isPdf: Boolean get() = mimeType == MIME_PDF

    companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PDF = "application/pdf"
        const val MAX_BYTES = 15 * 1024 * 1024 // same limit as the storage bucket
    }
}

class FileTooLargeException : Exception("File is larger than 15 MB")
