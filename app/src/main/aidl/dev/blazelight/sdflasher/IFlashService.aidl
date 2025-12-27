// IFlashService.aidl
package dev.blazelight.sdflasher;

import dev.blazelight.sdflasher.IFlashCallback;
import android.os.ParcelFileDescriptor;

// Service interface for flash operations running in root process
interface IFlashService {
    // Start flashing image to device
    // devicePath: block device path (e.g., /dev/block/mmcblk1)
    // imageFd: file descriptor for the image file
    // totalSize: uncompressed size of the image
    // compressionType: 0=NONE, 1=GZIP, 2=XZ, 3=ZIP
    // verify: whether to verify after writing
    void startFlash(
        String devicePath,
        in ParcelFileDescriptor imageFd,
        long totalSize,
        int compressionType,
        boolean verify,
        IFlashCallback callback
    );

    // Cancel ongoing flash operation
    void cancelFlash();

    // Check if flash is in progress
    boolean isFlashing();

    // Get list of removable block devices
    // Returns JSON array of device info
    String getRemovableDevices();

    // Unmount all partitions on a device
    boolean unmountDevice(String devicePath);
}
