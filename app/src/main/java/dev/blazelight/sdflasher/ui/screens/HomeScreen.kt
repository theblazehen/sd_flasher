package dev.blazelight.sdflasher.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.blazelight.sdflasher.domain.model.FlashStage
import dev.blazelight.sdflasher.ui.components.*
import dev.blazelight.sdflasher.ui.viewmodel.FlashViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FlashViewModel,
    onNavigateToFlash: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // File picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.selectImage(it) }
    }

    // Show root required dialog if needed
    if (!uiState.isRootAvailable && (uiState.isCheckingRoot || !uiState.isLoading)) {
        RootRequiredDialog(
            isCheckingRoot = uiState.isCheckingRoot,
            onRetry = { viewModel.retryRootAccess() },
            onExit = { (context as? Activity)?.finish() }
        )
    }

    // Show confirm dialog
    if (uiState.showConfirmDialog && uiState.selectedDevice != null && uiState.selectedImage != null) {
        ConfirmFlashDialog(
            device = uiState.selectedDevice!!,
            image = uiState.selectedImage!!,
            onConfirm = {
                viewModel.startFlash()
                onNavigateToFlash()
            },
            onDismiss = { viewModel.dismissConfirmDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SdCard,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "SD Flasher",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshDevices() },
                        enabled = !uiState.isLoading && uiState.isRootAvailable
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh devices"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Error message
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Image selection section
            Text(
                text = "Image File",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ImageCard(
                image = uiState.selectedImage,
                onSelectClick = {
                    imagePickerLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/x-raw-disk-image",
                            "application/gzip",
                            "application/x-gzip",
                            "application/x-xz",
                            "application/zip",
                            "*/*"  // Fallback
                        )
                    )
                },
                onClearClick = { viewModel.clearImage() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Device selection section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (uiState.isLoading && uiState.isRootAvailable) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.devices.isEmpty() && !uiState.isLoading && uiState.isRootAvailable) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No removable devices found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Insert an SD card and tap refresh",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.devices) { device ->
                        DeviceCard(
                            device = device,
                            isSelected = uiState.selectedDevice?.path == device.path,
                            onClick = { viewModel.selectDevice(device) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Verify toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Verify after flashing",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.verifyAfterFlash,
                    onCheckedChange = { viewModel.setVerifyAfterFlash(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Flash button
            Button(
                onClick = { viewModel.showConfirmDialog() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState.selectedDevice != null &&
                        uiState.selectedImage != null &&
                        uiState.isRootAvailable &&
                        uiState.flashProgress.stage == FlashStage.IDLE
            ) {
                Text(
                    text = "Flash Image",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
