package dev.blazelight.sdflasher.util

import dev.blazelight.sdflasher.domain.model.CompressionType
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Creates a decompressing input stream based on compression type.
 */
object DecompressingInputStream {

    /**
     * Wrap the input stream with appropriate decompressor.
     * For ZIP files, returns the first entry's stream.
     *
     * @param inputStream The compressed input stream
     * @param compressionType The type of compression
     * @return A stream that yields decompressed data
     */
    fun wrap(inputStream: InputStream, compressionType: CompressionType): InputStream {
        val buffered = if (inputStream.markSupported()) {
            inputStream
        } else {
            BufferedInputStream(inputStream, 64 * 1024) // 64KB buffer
        }

        return when (compressionType) {
            CompressionType.NONE -> buffered
            CompressionType.GZIP -> GzipCompressorInputStream(buffered)
            CompressionType.XZ -> XZCompressorInputStream(buffered)
            CompressionType.ZIP -> {
                val zipStream = ZipArchiveInputStream(buffered)
                // Find the first .img file in the archive
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && 
                        (entry.name.endsWith(".img", ignoreCase = true) ||
                         !entry.name.contains("."))) {
                        // Return stream positioned at this entry
                        return zipStream
                    }
                    entry = zipStream.nextEntry
                }
                // If no .img found, just use the first entry
                zipStream
            }
        }
    }

    /**
     * Detect compression type from stream and wrap accordingly.
     */
    fun wrapAutoDetect(inputStream: InputStream): Pair<InputStream, CompressionType> {
        val buffered = if (inputStream.markSupported()) {
            inputStream
        } else {
            BufferedInputStream(inputStream, 64 * 1024)
        }

        val detected = CompressionDetector.detect(buffered)
        val type = when (detected) {
            CompressionDetector.Compression.GZIP -> CompressionType.GZIP
            CompressionDetector.Compression.XZ -> CompressionType.XZ
            CompressionDetector.Compression.ZIP -> CompressionType.ZIP
            CompressionDetector.Compression.NONE -> CompressionType.NONE
        }

        return Pair(wrap(buffered, type), type)
    }
}
