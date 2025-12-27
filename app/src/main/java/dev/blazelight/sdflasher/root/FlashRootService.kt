package dev.blazelight.sdflasher.root

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import dev.blazelight.sdflasher.IFlashCallback
import dev.blazelight.sdflasher.IFlashService
import dev.blazelight.sdflasher.data.repository.BlockDeviceRepository
import dev.blazelight.sdflasher.domain.model.CompressionType
import dev.blazelight.sdflasher.util.DecompressingInputStream
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Root service that performs privileged flash operations.
 * Runs in a separate root process with elevated privileges.
 */
class FlashRootService : RootService() {

    private val binder = FlashServiceImpl()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private inner class FlashServiceImpl : IFlashService.Stub() {

        private var flashJob: Job? = null
        private val isCancelled = AtomicBoolean(false)
        private val isFlashingFlag = AtomicBoolean(false)

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val blockDeviceRepository = BlockDeviceRepository()

        override fun startFlash(
            devicePath: String,
            imageFd: ParcelFileDescriptor,
            totalSize: Long,
            compressionType: Int,
            verify: Boolean,
            callback: IFlashCallback
        ) {
            if (isFlashingFlag.get()) {
                callback.onError("Flash already in progress")
                return
            }

            isCancelled.set(false)
            isFlashingFlag.set(true)

            flashJob = scope.launch {
                try {
                    doFlash(devicePath, imageFd, totalSize, compressionType, verify, callback)
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error")
                    callback.onComplete(false, e.message ?: "Flash failed")
                } finally {
                    isFlashingFlag.set(false)
                    try {
                        imageFd.close()
                    } catch (_: Exception) {}
                }
            }
        }

        private suspend fun doFlash(
            devicePath: String,
            imageFd: ParcelFileDescriptor,
            totalSize: Long,
            compressionTypeInt: Int,
            verify: Boolean,
            callback: IFlashCallback
        ) {
            // Stage: Preparing
            callback.onStageChanged(0)

            // Unmount the device first
            callback.onStageChanged(2) // UNMOUNTING
            val unmountResult = runBlocking { blockDeviceRepository.unmountDevice(devicePath) }
            if (unmountResult.isFailure) {
                throw Exception("Failed to unmount device: ${unmountResult.exceptionOrNull()?.message}")
            }

            // Open input stream with decompression
            val rawStream = FileInputStream(imageFd.fileDescriptor)
            val compressionType = CompressionType.entries.getOrElse(compressionTypeInt) { CompressionType.NONE }
            val inputStream: InputStream = DecompressingInputStream.wrap(rawStream, compressionType)

            // Stage: Writing
            callback.onStageChanged(1)

            // Use libsu-nio for writing to block device
            val fsManager = FileSystemManager.getLocal()
            val targetDevice: ExtendedFile = fsManager.getFile(devicePath)

            if (!targetDevice.exists()) {
                throw Exception("Target device not found: $devicePath")
            }

            // Get actual device size for verification
            val deviceSizeResult = Shell.cmd("cat /sys/block/${devicePath.substringAfterLast("/")}/size").exec()
            val deviceSizeBytes = (deviceSizeResult.out.firstOrNull()?.toLongOrNull() ?: 0) * 512

            if (deviceSizeBytes > 0 && totalSize > deviceSizeBytes) {
                throw Exception("Image size ($totalSize bytes) exceeds device size ($deviceSizeBytes bytes)")
            }

            // Write the image
            var bytesWritten = 0L
            var lastReportTime = System.currentTimeMillis()
            var lastReportBytes = 0L
            val bufferSize = 4 * 1024 * 1024 // 4MB buffer for efficiency
            val buffer = ByteArray(bufferSize)

            targetDevice.newOutputStream().use { outputStream ->
                while (!isCancelled.get()) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    outputStream.write(buffer, 0, read)
                    bytesWritten += read

                    // Report progress every 500ms
                    val now = System.currentTimeMillis()
                    if (now - lastReportTime >= 500) {
                        val elapsed = (now - lastReportTime) / 1000.0
                        val bytesThisInterval = bytesWritten - lastReportBytes
                        val speed = (bytesThisInterval / elapsed).toLong()

                        try {
                            callback.onProgress(bytesWritten, totalSize, speed)
                        } catch (e: RemoteException) {
                            // Client disconnected
                            isCancelled.set(true)
                        }

                        lastReportTime = now
                        lastReportBytes = bytesWritten
                    }
                }
            }

            inputStream.close()

            if (isCancelled.get()) {
                callback.onStageChanged(8) // CANCELLED
                callback.onComplete(false, "Flash cancelled by user")
                return
            }

            // Stage: Syncing
            callback.onStageChanged(4)
            Shell.cmd("sync").exec()
            Shell.cmd("blockdev --flushbufs $devicePath").exec()

            // Final progress report
            callback.onProgress(bytesWritten, totalSize, 0)

            if (verify) {
                // Stage: Verifying
                callback.onStageChanged(5)
                
                // TODO: Implement verification by re-reading and comparing checksums
                // For now, just report success
            }

            // Stage: Complete
            callback.onStageChanged(6)
            callback.onComplete(true, "Successfully flashed $bytesWritten bytes")
        }

        override fun cancelFlash() {
            isCancelled.set(true)
            flashJob?.cancel()
        }

        override fun isFlashing(): Boolean {
            return isFlashingFlag.get()
        }

        override fun getRemovableDevices(): String {
            val result = runBlocking { blockDeviceRepository.getRemovableDevices() }
            return if (result.isSuccess) {
                blockDeviceRepository.getDevicesAsJson(result.getOrDefault(emptyList()))
            } else {
                "[]"
            }
        }

        override fun unmountDevice(devicePath: String): Boolean {
            val result = runBlocking { blockDeviceRepository.unmountDevice(devicePath) }
            return result.isSuccess
        }
    }
}
