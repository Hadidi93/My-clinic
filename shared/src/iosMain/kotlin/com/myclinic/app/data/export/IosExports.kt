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

/** iPhone PDF export: added with the iPhone PDF writer (UIGraphicsPDFRenderer). */
class IosRecordPdfWriter : RecordPdfWriter {
    override suspend fun write(doc: RecordDocument, doctorName: String, title: String): ExportedFile =
        error("PDF export is not available on iPhone yet")

    override fun cleanUp() = cleanUpExports()
}
