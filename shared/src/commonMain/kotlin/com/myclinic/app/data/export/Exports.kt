package com.myclinic.app.data.export

import com.myclinic.domain.record.CsvFile
import com.myclinic.domain.record.RecordDocument
import kotlinx.datetime.LocalDate

/**
 * A file made on the phone for the share sheet (PDF of a record, research
 * ZIP). It sits in a private temporary folder that is emptied at every app start.
 */
data class ExportedFile(val path: String, val mimeType: String, val title: String)

/** Draws a record as a PDF (Android's PDF writer / iPhone's). */
interface RecordPdfWriter {
    suspend fun write(doc: RecordDocument, doctorName: String, title: String): ExportedFile
    /** Removes earlier exports (at app start and before each export). */
    fun cleanUp()
}

/** Packs the research spreadsheets into one ZIP. */
interface ResearchZipWriter {
    suspend fun write(files: List<CsvFile>, today: LocalDate, title: String): ExportedFile
}
