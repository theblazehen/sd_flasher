package dev.blazelight.sdflasher.domain.model

/**
 * Represents the current progress of a flash operation.
 */
data class FlashProgress(
    val bytesWritten: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val stage: FlashStage = FlashStage.IDLE
) {
    val percentage: Float
        get() = if (totalBytes > 0) (bytesWritten.toFloat() / totalBytes) * 100f else 0f

    val percentageInt: Int
        get() = percentage.toInt()

    val bytesWrittenFormatted: String
        get() = BlockDevice.formatBytes(bytesWritten)

    val totalBytesFormatted: String
        get() = BlockDevice.formatBytes(totalBytes)

    val speedFormatted: String
        get() = "${BlockDevice.formatBytes(speedBytesPerSec)}/s"

    val etaSeconds: Long
        get() = if (speedBytesPerSec > 0) {
            (totalBytes - bytesWritten) / speedBytesPerSec
        } else {
            -1
        }

    val etaFormatted: String
        get() {
            val seconds = etaSeconds
            return when {
                seconds < 0 -> "Calculating..."
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
                else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            }
        }
}

/**
 * Stages of the flash operation.
 */
enum class FlashStage(val displayName: String) {
    IDLE("Ready"),
    PREPARING("Preparing..."),
    UNMOUNTING("Unmounting partitions..."),
    WRITING("Writing image..."),
    SYNCING("Syncing..."),
    VERIFYING("Verifying..."),
    COMPLETE("Complete"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    val isTerminal: Boolean
        get() = this == COMPLETE || this == FAILED || this == CANCELLED

    val isActive: Boolean
        get() = this == PREPARING || this == UNMOUNTING || this == WRITING || 
                this == SYNCING || this == VERIFYING

    companion object {
        fun fromInt(value: Int): FlashStage = entries.getOrElse(value) { IDLE }
    }
}
