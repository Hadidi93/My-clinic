package com.myclinic.app.data.export

import android.content.Context
import com.myclinic.domain.record.CsvFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.datetime.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs the research spreadsheets into one ZIP in the private exports folder
 * (cleared at every app start, like the PDF export) and offers it through
 * the share sheet. CSV files start with a UTF-8 mark so Excel shows Arabic correctly.
 */
class ResearchExporter(
    private val context: Context,
) : ResearchZipWriter {
    private val dir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    override suspend fun write(files: List<CsvFile>, today: LocalDate, title: String): ExportedFile =
        ExportedFile(writeZip(files, today).absolutePath, "application/zip", title)

    private suspend fun writeZip(files: List<CsvFile>, today: LocalDate): File = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        val zip = File(dir, "my-clinic-research-$today.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            files.forEach { f ->
                out.putNextEntry(ZipEntry(f.name))
                if (f.name.endsWith(".csv")) out.write(UTF8_BOM)
                out.write(f.text.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        zip
    }


    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
