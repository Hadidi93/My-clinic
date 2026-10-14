package com.myclinic.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/** The iPhone's ZIP writer must produce files any unzip tool reads. Demo data only. */
class StoredZipTest {
    @Test
    fun `java's unzip reads the files back, with Arabic names and text`() {
        val files = listOf(
            "patients.csv" to "study_id,age\r\nP001,54\r\n".encodeToByteArray(),
            "ملاحظات.txt" to "تجربة".encodeToByteArray(),
            "empty.txt" to ByteArray(0),
        )
        val read = mutableMapOf<String, String>()
        ZipInputStream(StoredZip.build(files).inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                read[entry.name] = zip.readBytes().decodeToString()
            }
        }
        assertEquals(files.associate { it.first to it.second.decodeToString() }, read)
    }

    @Test
    fun `crc matches java's`() {
        val data = "My Clinic demo".encodeToByteArray()
        assertEquals(CRC32().apply { update(data) }.value.toInt(), StoredZip.crc32(data))
    }
}
