package dev.blazelight.sdflasher.data.repository

import com.topjohnwu.superuser.Shell
import dev.blazelight.sdflasher.domain.model.BlockDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for detecting and managing block devices via root shell.
 */
@Singleton
class BlockDeviceRepository @Inject constructor() {

    /**
     * Get list of removable block devices suitable for flashing.
     */
    suspend fun getRemovableDevices(): Result<List<BlockDevice>> = withContext(Dispatchers.IO) {
        try {
            if (Shell.isAppGrantedRoot() != true) {
                return@withContext Result.failure(SecurityException("Root access not granted"))
            }

            val devices = mutableListOf<BlockDevice>()

            // List all block devices
            val lsResult = Shell.cmd("ls /sys/block/").exec()
            if (!lsResult.isSuccess) {
                return@withContext Result.failure(Exception("Failed to list block devices"))
            }

            for (deviceName in lsResult.out) {
                if (deviceName.isBlank()) continue
                
                // Skip loop devices, dm devices, ram disks, and zram
                if (deviceName.startsWith("loop") ||
                    deviceName.startsWith("dm-") ||
                    deviceName.startsWith("ram") ||
                    deviceName.startsWith("zram")) {
                    continue
                }

                // Check if removable
                val removableResult = Shell.cmd("cat /sys/block/$deviceName/removable").exec()
                val isRemovable = removableResult.out.firstOrNull()?.trim() == "1"

                // Also check if it's an SD card by looking at device path
                // Some SD cards may not report as removable but are under mmcblk
                val isSdCard = deviceName.startsWith("mmcblk") && !deviceName.contains("boot") 
                               && !deviceName.contains("rpmb")

                if (!isRemovable && !isSdCard) continue

                // Get device size
                val sizeResult = Shell.cmd("cat /sys/block/$deviceName/size").exec()
                val sectors = sizeResult.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0
                val sizeBytes = sectors * 512 // Each sector is 512 bytes

                // Skip if too small (< 64MB, likely not a real storage device)
                if (sizeBytes < 64 * 1024 * 1024) continue

                // Get partitions
                val partitions = mutableListOf<String>()
                val partResult = Shell.cmd("ls /sys/block/$deviceName/ | grep -E '^${deviceName}p?[0-9]+'").exec()
                if (partResult.isSuccess) {
                    partitions.addAll(partResult.out.filter { it.isNotBlank() }
                        .map { "/dev/block/$it" })
                }

                val device = BlockDevice(
                    path = "/dev/block/$deviceName",
                    name = deviceName,
                    sizeBytes = sizeBytes,
                    isRemovable = isRemovable,
                    partitions = partitions
                )
                devices.add(device)
            }

            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unmount all partitions on a device.
     */
    suspend fun unmountDevice(devicePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceName = devicePath.substringAfterLast("/")
            
            // Find all mounted partitions for this device
            val mountsResult = Shell.cmd("mount | grep '/dev/block/$deviceName'").exec()
            
            for (line in mountsResult.out) {
                val mountPoint = line.split(" on ").getOrNull(1)?.split(" type")?.firstOrNull()
                if (mountPoint != null) {
                    val umountResult = Shell.cmd("umount '$mountPoint'").exec()
                    if (!umountResult.isSuccess) {
                        // Try force unmount
                        Shell.cmd("umount -f '$mountPoint'").exec()
                    }
                }
            }

            // Also unmount by device path directly
            Shell.cmd("umount ${devicePath}* 2>/dev/null").exec()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if root access is available.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        Shell.isAppGrantedRoot() == true
    }

    /**
     * Get device info as JSON (for AIDL transfer).
     */
    fun getDevicesAsJson(devices: List<BlockDevice>): String {
        val jsonArray = JSONArray()
        for (device in devices) {
            val obj = JSONObject().apply {
                put("path", device.path)
                put("name", device.name)
                put("sizeBytes", device.sizeBytes)
                put("isRemovable", device.isRemovable)
                put("partitions", JSONArray(device.partitions))
                device.label?.let { put("label", it) }
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    /**
     * Parse device info from JSON.
     */
    fun parseDevicesFromJson(json: String): List<BlockDevice> {
        val devices = mutableListOf<BlockDevice>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val partitionsArray = obj.getJSONArray("partitions")
            val partitions = (0 until partitionsArray.length()).map { 
                partitionsArray.getString(it) 
            }
            devices.add(BlockDevice(
                path = obj.getString("path"),
                name = obj.getString("name"),
                sizeBytes = obj.getLong("sizeBytes"),
                isRemovable = obj.getBoolean("isRemovable"),
                partitions = partitions,
                label = obj.optString("label").takeIf { it.isNotEmpty() }
            ))
        }
        return devices
    }
}
