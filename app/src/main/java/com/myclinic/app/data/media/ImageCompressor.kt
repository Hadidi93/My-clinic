package com.myclinic.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max

/**
 * Shrinks a picked photo and re-encodes it as JPEG. Re-encoding also drops
 * EXIF metadata (GPS location, camera details), so none of it is uploaded.
 */
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun toJpeg(uri: Uri, maxDimension: Int, quality: Int = 85): ByteArray = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val largest = max(info.size.width, info.size.height)
            if (largest > maxDimension) {
                val scale = maxDimension.toFloat() / largest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
