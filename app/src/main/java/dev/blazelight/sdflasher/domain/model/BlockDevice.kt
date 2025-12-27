package dev.blazelight.sdflasher.domain.model

/**
 * Represents a block device that can be used as a flash target.
 */
data class BlockDevice(
    val path: String,           // Full path: /dev/block/mmcblk1
    val name: String,           // Device name: mmcblk1
    val sizeBytes: Long,        // Total size in bytes
    val isRemovable: Boolean,   // True if removable media
    val partitions: List<String>, // List of partition paths
    val label: String? = null   // Volume label if available
) {
    val sizeFormatted: String
        get() = formatBytes(sizeBytes)

    val hasPartitions: Boolean
        get() = partitions.isNotEmpty()

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unitIndex = -1
            do {
                value /= 1024
                unitIndex++
            } while (value >= 1024 && unitIndex < units.size - 1)
            return "%.1f %s".format(value, units[unitIndex])
        }
    }
}
