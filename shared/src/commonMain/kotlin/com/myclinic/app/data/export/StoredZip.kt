package com.myclinic.app.data.export

/**
 * Builds a ZIP file without compression ("stored"), which every unzip tool
 * and spreadsheet program opens. Used on iPhone, which has no ZIP API; the
 * research files are small, so not compressing them costs little.
 */
object StoredZip {

    fun build(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = Bytes()
        val central = Bytes()
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size
            // Local file header
            out.int(0x04034b50); out.short(20); out.short(UTF8_FLAG); out.short(0) // version, flags, method = stored
            out.short(0); out.short(0x21) // time 00:00, date 1980-01-01
            out.int(crc); out.int(data.size); out.int(data.size)
            out.short(nameBytes.size); out.short(0)
            out.bytes(nameBytes); out.bytes(data)
            // Central directory entry
            central.int(0x02014b50); central.short(20); central.short(20); central.short(UTF8_FLAG); central.short(0)
            central.short(0); central.short(0x21)
            central.int(crc); central.int(data.size); central.int(data.size)
            central.short(nameBytes.size); central.short(0); central.short(0) // name, extra, comment lengths
            central.short(0); central.short(0); central.int(0) // disk, internal and external attributes
            central.int(offset)
            central.bytes(nameBytes)
        }
        val centralOffset = out.size
        out.bytes(central.toByteArray())
        // End of central directory
        out.int(0x06054b50); out.short(0); out.short(0)
        out.short(entries.size); out.short(entries.size)
        out.int(central.size); out.int(centralOffset); out.short(0)
        return out.toByteArray()
    }

    /** CRC-32 (the same checksum as java.util.zip.CRC32). */
    fun crc32(data: ByteArray): Int {
        var crc = 0xFFFFFFFF.toInt()
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1 }
        }
        return crc.inv()
    }

    private const val UTF8_FLAG = 0x0800 // file names are UTF-8

    private class Bytes {
        private var buf = ByteArray(1024)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
        }
        fun short(v: Int) { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte() }
        fun int(v: Int) { short(v and 0xFFFF); short(v ushr 16) }
        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
        fun toByteArray() = buf.copyOf(size)
    }
}
