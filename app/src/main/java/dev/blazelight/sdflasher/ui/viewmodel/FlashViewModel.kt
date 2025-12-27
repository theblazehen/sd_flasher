package dev.blazelight.sdflasher.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.blazelight.sdflasher.IFlashCallback
import dev.blazelight.sdflasher.IFlashService
import dev.blazelight.sdflasher.data.repository.BlockDeviceRepository
import dev.blazelight.sdflasher.data.repository.ImageRepository
import dev.blazelight.sdflasher.domain.model.BlockDevice
import dev.blazelight.sdflasher.domain.model.FlashProgress
import dev.blazelight.sdflasher.domain.model.FlashStage
import dev.blazelight.sdflasher.domain.model.ImageFile
import dev.blazelight.sdflasher.root.FlashRootService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashUiState(
    val isLoading: Boolean = true,
    val isRootAvailable: Boolean = false,
    val isCheckingRoot: Boolean = true,
    val devices: List<BlockDevice> = emptyList(),
    val selectedDevice: BlockDevice? = null,
    val selectedImage: ImageFile? = null,
    val flashProgress: FlashProgress = FlashProgress(),
    val showConfirmDialog: Boolean = false,
    val verifyAfterFlash: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class FlashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockDeviceRepository: BlockDeviceRepository,
    private val imageRepository: ImageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashUiState())
    val uiState: StateFlow<FlashUiState> = _uiState.asStateFlow()

    private var flashService: IFlashService? = null
    private var serviceConnection: ServiceConnection? = null

    init {
        checkRootAccess()
    }

    private fun checkRootAccess() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingRoot = true) }

            Shell.getShell { shell ->
                val isRoot = shell.isRoot
                _uiState.update {
                    it.copy(
                        isRootAvailable = isRoot,
                        isCheckingRoot = false,
                        isLoading = !isRoot
                    )
                }

                if (isRoot) {
                    connectToRootService()
                    refreshDevices()
                }
            }
        }
    }

    private fun connectToRootService() {
        val intent = Intent(context, FlashRootService::class.java)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                flashService = IFlashService.Stub.asInterface(service)
                refreshDevicesFromService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                flashService = null
            }
        }

        RootService.bind(intent, serviceConnection!!)
    }

    fun retryRootAccess() {
        checkRootAccess()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = blockDeviceRepository.getRemovableDevices()

            result.onSuccess { devices ->
                _uiState.update {
                    it.copy(
                        devices = devices,
                        isLoading = false,
                        // Clear selection if selected device no longer exists
                        selectedDevice = if (devices.any { d -> d.path == it.selectedDevice?.path }) {
                            it.selectedDevice
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to detect devices"
                    )
                }
            }
        }
    }

    private fun refreshDevicesFromService() {
        viewModelScope.launch {
            flashService?.let { service ->
                try {
                    val json = service.removableDevices
                    val devices = blockDeviceRepository.parseDevicesFromJson(json)
                    _uiState.update {
                        it.copy(devices = devices, isLoading = false)
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(errorMessage = "Failed to get devices: ${e.message}")
                    }
                }
            }
        }
    }

    fun selectDevice(device: BlockDevice) {
        _uiState.update { it.copy(selectedDevice = device, errorMessage = null) }
    }

    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            val result = imageRepository.getImageInfo(uri)

            result.onSuccess { imageFile ->
                _uiState.update { it.copy(selectedImage = imageFile, errorMessage = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = "Failed to read image: ${error.message}")
                }
            }
        }
    }

    fun clearImage() {
        _uiState.update { it.copy(selectedImage = null) }
    }

    fun setVerifyAfterFlash(verify: Boolean) {
        _uiState.update { it.copy(verifyAfterFlash = verify) }
    }

    fun showConfirmDialog() {
        if (_uiState.value.selectedDevice != null && _uiState.value.selectedImage != null) {
            _uiState.update { it.copy(showConfirmDialog = true) }
        }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun startFlash() {
        val state = _uiState.value
        val device = state.selectedDevice ?: return
        val image = state.selectedImage ?: return
        val service = flashService

        if (service == null) {
            _uiState.update { it.copy(errorMessage = "Root service not connected") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showConfirmDialog = false,
                    flashProgress = FlashProgress(stage = FlashStage.PREPARING)
                )
            }

            try {
                // Open file descriptor for the image
                val pfd = context.contentResolver.openFileDescriptor(image.uri, "r")
                    ?: throw Exception("Failed to open image file")

                // Estimate uncompressed size
                val estimatedSize = imageRepository.estimateUncompressedSize(image)

                // Create callback for progress updates
                val callback = object : IFlashCallback.Stub() {
                    override fun onProgress(bytesWritten: Long, totalBytes: Long, speedBytesPerSec: Long) {
                        _uiState.update {
                            it.copy(
                                flashProgress = it.flashProgress.copy(
                                    bytesWritten = bytesWritten,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speedBytesPerSec
                                )
                            )
                        }
                    }

                    override fun onStageChanged(stage: Int) {
                        _uiState.update {
                            it.copy(
                                flashProgress = it.flashProgress.copy(
                                    stage = FlashStage.fromInt(stage)
                                )
                            )
                        }
                    }

                    override fun onComplete(success: Boolean, message: String?) {
                        _uiState.update {
                            it.copy(
                                flashProgress = it.flashProgress.copy(
                                    stage = if (success) FlashStage.COMPLETE else FlashStage.FAILED
                                ),
                                errorMessage = if (!success) message else null
                            )
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        _uiState.update {
                            it.copy(
                                errorMessage = errorMessage,
                                flashProgress = it.flashProgress.copy(stage = FlashStage.FAILED)
                            )
                        }
                    }
                }

                // Start the flash operation
                service.startFlash(
                    device.path,
                    pfd,
                    estimatedSize,
                    image.compressionType.ordinal,
                    state.verifyAfterFlash,
                    callback
                )

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to start flash: ${e.message}",
                        flashProgress = FlashProgress(stage = FlashStage.FAILED)
                    )
                }
            }
        }
    }

    fun cancelFlash() {
        flashService?.cancelFlash()
        _uiState.update {
            it.copy(flashProgress = it.flashProgress.copy(stage = FlashStage.CANCELLED))
        }
    }

    fun resetFlash() {
        _uiState.update {
            it.copy(
                flashProgress = FlashProgress(),
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnection?.let {
            RootService.unbind(it)
        }
    }
}
