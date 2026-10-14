package com.myclinic.app.ui.files

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/** iPhone's [FileRenderer]: Skia decodes photos, PDFKit draws PDF pages. All in memory. */
@OptIn(ExperimentalForeignApi::class)
class IosFileRenderer : FileRenderer {

    override suspend fun image(bytes: ByteArray, maxPx: Int): ImageBitmap = withContext(Dispatchers.Default) {
        shrink(Image.makeFromEncoded(bytes), maxPx).toComposeImageBitmap()
    }

    override suspend fun pdfPages(bytes: ByteArray, widthPx: Int, maxPages: Int): Pair<List<ImageBitmap>, Boolean> =
        withContext(Dispatchers.Default) {
            val document = PDFDocument(data = bytes.toNSData()) ?: error("Not a PDF")
            val total = document.pageCount.toInt()
            val pages = (0 until minOf(total, maxPages)).mapNotNull { i ->
                val page = document.pageAtIndex(i.toULong()) ?: return@mapNotNull null
                val (w, h) = page.boundsForBox(kPDFDisplayBoxMediaBox).useContents { size.width to size.height }
                val scale = widthPx / w
                val picture = page.thumbnailOfSize(CGSizeMake(widthPx.toDouble(), h * scale), forBox = kPDFDisplayBoxMediaBox)
                val png = UIImagePNGRepresentation(picture)?.toByteArray() ?: return@mapNotNull null
                Image.makeFromEncoded(png).toComposeImageBitmap()
            }
            pages to (total > maxPages)
        }

    private fun shrink(image: Image, maxPx: Int): Image {
        val longest = maxOf(image.width, image.height)
        if (longest <= maxPx) return image
        val scale = maxPx.toFloat() / longest
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(image, Rect.makeWH(w.toFloat(), h.toFloat()))
        return surface.makeImageSnapshot()
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}
