package com.myclinic.app.ui.files

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Draws a clinical file for the screen, in memory only (each phone uses its
 * own image and PDF decoders). Nothing is kept on the phone's storage.
 */
interface FileRenderer {
    /** A photo, shrunk so its longest side is at most [maxPx]. */
    suspend fun image(bytes: ByteArray, maxPx: Int): ImageBitmap

    /** The PDF's pages, [widthPx] wide, at most [maxPages]; the flag says whether there were more. */
    suspend fun pdfPages(bytes: ByteArray, widthPx: Int, maxPages: Int): Pair<List<ImageBitmap>, Boolean>
}
