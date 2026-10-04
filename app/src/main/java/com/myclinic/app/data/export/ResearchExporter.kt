package com.myclinic.app.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.myclinic.domain.record.CsvFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Packs the research spreadsheets into one ZIP in the private exports folder
 * (cleared at every app start, like the PDF export) and offers it through
 * the share sheet. CSV files start with a UTF-8 mark so Excel shows Arabic correctly.
 */
class ResearchExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun write(files: List<CsvFile>, today: LocalDate): File = withContext(Dispatchers.IO) {
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

    fun shareIntent(file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
