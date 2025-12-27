package dev.blazelight.sdflasher.domain.model

import android.net.Uri

/**
 * Represents an image file to be flashed.
 */
data class ImageFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val compressionType: CompressionType
) {
    val sizeFormatted: String
        get() = BlockDevice.formatBytes(sizeBytes)

    val displayName: String
        get() = name.substringAfterLast("/")
}

/**
 * Supported compression types for disk images.
 */
enum class CompressionType(val extension: String, val displayName: String) {
    NONE("img", "Raw Image"),
    GZIP("gz", "GZip Compressed"),
    XZ("xz", "XZ Compressed"),
    ZIP("zip", "ZIP Archive");

    companion object {
        fun fromExtension(filename: String): CompressionType {
            val lower = filename.lowercase()
            return when {
                lower.endsWith(".img.gz") || lower.endsWith(".gz") -> GZIP
                lower.endsWith(".img.xz") || lower.endsWith(".xz") -> XZ
                lower.endsWith(".zip") -> ZIP
                else -> NONE
            }
        }
    }
}
