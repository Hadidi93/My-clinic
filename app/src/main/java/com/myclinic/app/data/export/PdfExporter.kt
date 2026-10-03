package com.myclinic.app.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.myclinic.app.R
import com.myclinic.domain.record.RecordDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import javax.inject.Inject

/**
 * Draws a [RecordDocument] as an A4 PDF with Android's built-in PDF writer
 * (Arabic and right-to-left text are laid out by Android). The file is
 * written to a private temporary folder, offered through the share sheet,
 * and deleted the next time the app starts.
 */
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Removes earlier exports (called at start and before each export). */
    fun cleanUp() {
        dir.listFiles()?.forEach { it.delete() }
    }

    suspend fun write(doc: RecordDocument, doctorName: String): File = withContext(Dispatchers.Default) {
        cleanUp()
        val pdf = PdfDocument()
        val title = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val heading = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0, 95, 115) }
        val body = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.BLACK }
        val small = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.DKGRAY }

        val generated = LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        val footer = context.getString(R.string.export_footer, doctorName, generated)
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            page?.let { p ->
                val canvas = p.canvas
                val text = "$footer · ${context.getString(R.string.export_page, pageNumber)}"
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, small, CONTENT_WIDTH).build()
                canvas.save()
                canvas.translate(MARGIN, PAGE_HEIGHT - MARGIN + 6)
                layout.draw(canvas)
                canvas.restore()
                pdf.finishPage(p)
            }
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            y = MARGIN
        }

        fun draw(text: String, paint: TextPaint, spaceAfter: Float) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL) // follows the text: right-aligned for Arabic
                .setLineSpacing(1.5f, 1f)
                .build()
            // Long text: split it over pages line by line.
            var line = 0
            while (line < layout.lineCount) {
                if (page == null || y + (layout.getLineBottom(line) - layout.getLineTop(line)) > PAGE_HEIGHT - MARGIN - 16) newPage()
                val canvas = page!!.canvas
                var last = line
                while (last < layout.lineCount &&
                    y + (layout.getLineBottom(last) - layout.getLineTop(line)) <= PAGE_HEIGHT - MARGIN - 16
                ) last++
                if (last == line) last = line + 1
                canvas.save()
                canvas.translate(MARGIN, y - layout.getLineTop(line))
                canvas.clipRect(0f, layout.getLineTop(line).toFloat(), CONTENT_WIDTH.toFloat(), layout.getLineTop(last).toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += layout.getLineTop(last) - layout.getLineTop(line)
                line = last
            }
            y += spaceAfter
        }

        draw(context.getString(R.string.export_confidential), small, 6f)
        draw(doc.title, title, 4f)
        doc.subtitle.forEach { draw(it, body, 2f) }
        y += 10f
        doc.sections.forEach { s ->
            draw(s.heading, heading, 4f)
            s.lines.forEach { draw("• $it", body, 3f) }
            y += 8f
        }
        finishPage()

        val file = File(dir, "${UUID.randomUUID()}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        file
    }

    /** The Android share sheet for the PDF; the user picks where it goes. */
    fun shareIntent(file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private companion object {
        const val PAGE_WIDTH = 595 // A4 in points
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 80
    }
}
