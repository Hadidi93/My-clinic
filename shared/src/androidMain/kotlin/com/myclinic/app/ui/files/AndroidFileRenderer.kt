package com.myclinic.app.ui.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Android's [FileRenderer]: BitmapFactory for photos, PdfRenderer for PDFs. */
class AndroidFileRenderer(private val context: Context) : FileRenderer {

    override suspend fun image(bytes: ByteArray, maxPx: Int): ImageBitmap = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > maxPx) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Not an image")
        bitmap.asImageBitmap()
    }

    /** PdfRenderer needs a file: it is written to private storage and deleted straight after opening. */
    override suspend fun pdfPages(bytes: ByteArray, widthPx: Int, maxPages: Int): Pair<List<ImageBitmap>, Boolean> = withContext(Dispatchers.IO) {
        val dir = File(context.noBackupFilesDir, "viewer").apply { mkdirs() }
        val tmp = File(dir, "${UUID.randomUUID()}.pdf")
        tmp.writeBytes(bytes)
        val fd = try {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            tmp.delete() // the open descriptor still reads it; nothing stays on disk
        }
        fd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = minOf(renderer.pageCount, maxPages)
                val pages = (0 until count).map { i ->
                    renderer.openPage(i).use { page ->
                        val scale = widthPx.toFloat() / page.width
                        val bitmap = Bitmap.createBitmap(widthPx, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap.asImageBitmap()
                    }
                }
                pages to (renderer.pageCount > maxPages)
            }
        }
    }
}
