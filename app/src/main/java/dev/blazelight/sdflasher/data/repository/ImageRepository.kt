package dev.blazelight.sdflasher.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.blazelight.sdflasher.domain.model.CompressionType
import dev.blazelight.sdflasher.domain.model.ImageFile
import dev.blazelight.sdflasher.util.CompressionDetector
import dev.blazelight.sdflasher.util.DecompressingInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for handling image file operations.
 */
@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Get image file info from a URI selected via SAF.
     */
    suspend fun getImageInfo(uri: Uri): Result<ImageFile> = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)

                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L

                    // Detect compression from filename first
                    var compressionType = CompressionType.fromExtension(name)

                    // If filename doesn't indicate compression, check magic bytes
                    if (compressionType == CompressionType.NONE) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val buffered = BufferedInputStream(stream)
                            val detected = CompressionDetector.detect(buffered)
                            compressionType = when (detected) {
                                CompressionDetector.Compression.GZIP -> CompressionType.GZIP
                                CompressionDetector.Compression.XZ -> CompressionType.XZ
                                CompressionDetector.Compression.ZIP -> CompressionType.ZIP
                                else -> CompressionType.NONE
                            }
                        }
                    }

                    return@withContext Result.success(
                        ImageFile(
                            uri = uri,
                            name = name,
                            sizeBytes = size,
                            compressionType = compressionType
                        )
                    )
                }
            }

            Result.failure(Exception("Failed to read file info"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Open an input stream for the image, with decompression if needed.
     */
    fun openImageStream(imageFile: ImageFile): InputStream? {
        val stream = context.contentResolver.openInputStream(imageFile.uri)
            ?: return null

        return DecompressingInputStream.wrap(stream, imageFile.compressionType)
    }

    /**
     * Get a raw (non-decompressing) input stream for the image.
     */
    fun openRawStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    /**
     * Estimate uncompressed size for compressed images.
     * This is an estimate and may not be exact.
     */
    suspend fun estimateUncompressedSize(imageFile: ImageFile): Long = withContext(Dispatchers.IO) {
        when (imageFile.compressionType) {
            CompressionType.NONE -> imageFile.sizeBytes
            CompressionType.GZIP -> {
                // GZIP stores uncompressed size in last 4 bytes (mod 2^32)
                // This only works for files < 4GB
                try {
                    context.contentResolver.openInputStream(imageFile.uri)?.use { stream ->
                        val size = stream.available().toLong()
                        if (size > 4) {
                            stream.skip(size - 4)
                            val bytes = ByteArray(4)
                            stream.read(bytes)
                            // Little-endian
                            val uncompressed = (bytes[0].toLong() and 0xFF) or
                                    ((bytes[1].toLong() and 0xFF) shl 8) or
                                    ((bytes[2].toLong() and 0xFF) shl 16) or
                                    ((bytes[3].toLong() and 0xFF) shl 24)
                            return@withContext uncompressed
                        }
                    }
                } catch (_: Exception) {}
                // Fallback: estimate 3x compression ratio
                imageFile.sizeBytes * 3
            }
            CompressionType.XZ, CompressionType.ZIP -> {
                // XZ doesn't store uncompressed size easily accessible
                // Estimate based on typical compression ratio (~3-5x for disk images)
                imageFile.sizeBytes * 4
            }
        }
    }
}
