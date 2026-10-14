package com.myclinic.app.data.export

import com.myclinic.app.ui.files.toNSData
import com.myclinic.domain.record.CsvFile
import com.myclinic.domain.record.RecordDocument
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToFile
import com.myclinic.app.ui.patients.formatLocalDateTime
import com.myclinic.domain.record.Dates
import com.myclinic.shared.resources.Res
import com.myclinic.shared.resources.export_confidential
import com.myclinic.shared.resources.export_footer
import com.myclinic.shared.resources.export_page
import kotlinx.cinterop.useContents
import org.jetbrains.compose.resources.getString
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSAttributedString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.NSStringDrawingUsesLineFragmentOrigin
import platform.UIKit.NSTextAlignmentNatural
import platform.UIKit.NSWritingDirectionNatural
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererContext
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.boundingRectWithSize
import platform.UIKit.drawWithRect
import kotlin.uuid.Uuid

/** The private temporary folder for exports; emptied at every app start (see [cleanUpExports]). */
internal fun exportsDir(): String {
    val dir = NSTemporaryDirectory() + "exports"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

@OptIn(ExperimentalForeignApi::class)
fun cleanUpExports() {
    val manager = NSFileManager.defaultManager
    val dir = exportsDir()
    manager.contentsOfDirectoryAtPath(dir, error = null)?.forEach { manager.removeItemAtPath("$dir/$it", error = null) }
}

/** iPhone research export: the CSV files in an uncompressed ZIP (UTF-8 mark so Excel shows Arabic). */
class IosResearchZipWriter : ResearchZipWriter {
    override suspend fun write(files: List<CsvFile>, today: LocalDate, title: String): ExportedFile = withContext(Dispatchers.IO) {
        cleanUpExports()
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val zip = StoredZip.build(files.map { f ->
            val text = f.text.encodeToByteArray()
            f.name to (if (f.name.endsWith(".csv")) bom + text else text)
        })
        val path = "${exportsDir()}/my-clinic-research-$today.zip"
        zip.toNSData().writeToFile(path, atomically = true)
        ExportedFile(path, "application/zip", title)
    }
}

/**
 * iPhone PDF export: draws the record on A4 pages with UIKit's PDF writer.
 * Text follows its own direction (Arabic right-to-left). A footer on every
 * page names the exporting doctor and the time, like the Android export.
 */
class IosRecordPdfWriter : RecordPdfWriter {

    override suspend fun write(doc: RecordDocument, doctorName: String, title: String): ExportedFile {
        val confidential = getString(Res.string.export_confidential)
        val footer = getString(Res.string.export_footer)
            .replace("%1\$s", doctorName).replace("%2\$s", formatLocalDateTime(Dates.now()))
        val pageLabel = getString(Res.string.export_page)
        return withContext(Dispatchers.Default) {
            cleanUpExports()
            val path = "${exportsDir()}/${Uuid.random()}.pdf"
            val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, PAGE_W, PAGE_H), format = UIGraphicsPDFRendererFormat())
            renderer.writePDFToURL(NSURL.fileURLWithPath(path), withActions = { context ->
                if (context != null) Pages(context, footer, pageLabel).apply {
                    draw(confidential, small, 6.0)
                    draw(doc.title, titleStyle, 4.0)
                    doc.subtitle.forEach { draw(it, body, 2.0) }
                    y += 10.0
                    doc.sections.forEach { section ->
                        draw(section.heading, heading, 4.0)
                        section.lines.forEach { draw("• $it", body, 3.0) }
                        y += 8.0
                    }
                    finish()
                }
            }, error = null)
            ExportedFile(path, "application/pdf", title)
        }
    }

    override fun cleanUp() = cleanUpExports()

    private class Style(val font: UIFont, val color: UIColor)

    private val titleStyle = Style(UIFont.boldSystemFontOfSize(18.0), UIColor.blackColor)
    private val heading = Style(UIFont.boldSystemFontOfSize(13.0), UIColor.colorWithRed(0.0, 95 / 255.0, 115 / 255.0, 1.0))
    private val body = Style(UIFont.systemFontOfSize(10.5), UIColor.blackColor)
    private val small = Style(UIFont.systemFontOfSize(8.5), UIColor.darkGrayColor)

    /** Lays text out top to bottom, starting a new page when the current one is full. */
    private inner class Pages(
        private val context: UIGraphicsPDFRendererContext,
        private val footer: String,
        private val pageLabel: String,
    ) {
        var y = 0.0
        private var page = 0

        private fun text(text: String, style: Style): NSAttributedString {
            val paragraph = NSMutableParagraphStyle().apply {
                setAlignment(NSTextAlignmentNatural)
                setBaseWritingDirection(NSWritingDirectionNatural)
                setLineSpacing(2.0)
            }
            return NSAttributedString.create(
                string = text,
                attributes = mapOf<Any?, Any?>(
                    NSFontAttributeName to style.font,
                    NSForegroundColorAttributeName to style.color,
                    NSParagraphStyleAttributeName to paragraph,
                ),
            )
        }

        private fun height(t: NSAttributedString): Double =
            t.boundingRectWithSize(CGSizeMake(CONTENT_W, 100_000.0), NSStringDrawingUsesLineFragmentOrigin, null)
                .useContents { size.height }

        private fun newPage() {
            if (page > 0) drawFooter()
            context.beginPage()
            page++
            y = MARGIN
        }

        private fun drawFooter() {
            val t = text("$footer · ${pageLabel.replace("%1\$d", page.toString())}", small)
            t.drawWithRect(CGRectMake(MARGIN, PAGE_H - MARGIN + 6.0, CONTENT_W, 30.0), NSStringDrawingUsesLineFragmentOrigin, null)
        }

        fun draw(value: String, style: Style, spaceAfter: Double) {
            // Long text is split into pieces that each fit on a page.
            for (piece in pieces(value)) {
                val t = text(piece, style)
                val h = height(t)
                if (page == 0 || y + h > PAGE_H - MARGIN - 16.0) newPage()
                t.drawWithRect(CGRectMake(MARGIN, y, CONTENT_W, h), NSStringDrawingUsesLineFragmentOrigin, null)
                y += h
            }
            y += spaceAfter
        }

        fun finish() {
            if (page == 0) newPage()
            drawFooter()
        }

        private fun pieces(value: String): List<String> = value.split('\n').flatMap { line ->
            if (line.length <= 1500) listOf(line) else line.chunked(1500)
        }
    }

    private companion object {
        const val PAGE_W = 595.0 // A4 in points
        const val PAGE_H = 842.0
        const val MARGIN = 40.0
        const val CONTENT_W = PAGE_W - 80.0
    }
}
