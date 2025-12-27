// IFlashCallback.aidl
package dev.blazelight.sdflasher;

// Callback interface for flash progress updates
interface IFlashCallback {
    void onProgress(long bytesWritten, long totalBytes, long speedBytesPerSec);
    void onStageChanged(int stage); // 0=PREPARING, 1=WRITING, 2=VERIFYING, 3=COMPLETE, 4=FAILED
    void onComplete(boolean success, String message);
    void onError(String errorMessage);
}
