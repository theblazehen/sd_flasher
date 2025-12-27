package dev.blazelight.sdflasher.util

import java.io.InputStream

/**
 * Detects compression type from magic bytes at the start of a stream.
 */
object CompressionDetector {

    // Magic byte signatures
    private val GZIP_MAGIC = byteArrayOf(0x1f.toByte(), 0x8b.toByte())
    private val XZ_MAGIC = byteArrayOf(0xfd.toByte(), 0x37, 0x7a, 0x58, 0x5a, 0x00)
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

    enum class Compression {
        NONE, GZIP, XZ, ZIP
    }

    /**
     * Detect compression from magic bytes.
     * Note: The stream must support mark/reset, or wrap it in BufferedInputStream first.
     */
    fun detect(inputStream: InputStream): Compression {
        if (!inputStream.markSupported()) {
            throw IllegalArgumentException("InputStream must support mark/reset")
        }

        inputStream.mark(16)
        val header = ByteArray(16)
        val bytesRead = inputStream.read(header)
        inputStream.reset()

        if (bytesRead < 2) return Compression.NONE

        return when {
            startsWith(header, GZIP_MAGIC) -> Compression.GZIP
            startsWith(header, XZ_MAGIC) -> Compression.XZ
            startsWith(header, ZIP_MAGIC) -> Compression.ZIP
            else -> Compression.NONE
        }
    }

    private fun startsWith(data: ByteArray, magic: ByteArray): Boolean {
        if (data.size < magic.size) return false
        for (i in magic.indices) {
            if (data[i] != magic[i]) return false
        }
        return true
    }
}
