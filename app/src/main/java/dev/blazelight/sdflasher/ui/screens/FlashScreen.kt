package dev.blazelight.sdflasher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.blazelight.sdflasher.domain.model.FlashStage
import dev.blazelight.sdflasher.ui.viewmodel.FlashViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashScreen(
    viewModel: FlashViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val progress = uiState.flashProgress

    // Handle back press during flash
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = progress.stage.isActive) {
        showCancelDialog = true
    }

    // Cancel confirmation dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Flash?") },
            text = { Text("The flash operation is still in progress. Are you sure you want to cancel?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelFlash()
                        showCancelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel Flash")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashing") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (progress.stage.isActive) {
                                showCancelDialog = true
                            } else {
                                viewModel.resetFlash()
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status icon
            val iconColor by animateColorAsState(
                targetValue = when (progress.stage) {
                    FlashStage.COMPLETE -> Color(0xFF4CAF50)
                    FlashStage.FAILED, FlashStage.CANCELLED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                label = "iconColor"
            )

            when (progress.stage) {
                FlashStage.COMPLETE -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = iconColor
                    )
                }
                FlashStage.FAILED, FlashStage.CANCELLED -> {
                    Icon(
                        imageVector = if (progress.stage == FlashStage.CANCELLED) {
                            Icons.Default.Cancel
                        } else {
                            Icons.Default.Error
                        },
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = iconColor
                    )
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 8.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stage text
            Text(
                text = progress.stage.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when (progress.stage) {
                    FlashStage.COMPLETE -> Color(0xFF4CAF50)
                    FlashStage.FAILED, FlashStage.CANCELLED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress bar (only during active flash)
            if (progress.stage.isActive || progress.stage == FlashStage.COMPLETE) {
                LinearProgressIndicator(
                    progress = { progress.percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    color = if (progress.stage == FlashStage.COMPLETE) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${progress.percentageInt}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${progress.bytesWrittenFormatted} / ${progress.totalBytesFormatted}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Speed and ETA
                if (progress.stage.isActive && progress.speedBytesPerSec > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speed: ${progress.speedFormatted}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "ETA: ${progress.etaFormatted}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error message
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action buttons
            when {
                progress.stage.isActive -> {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }
                progress.stage.isTerminal -> {
                    Button(
                        onClick = {
                            viewModel.resetFlash()
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (progress.stage == FlashStage.COMPLETE) "Done" else "Go Back",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Device info
            if (uiState.selectedDevice != null) {
                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Target: ${uiState.selectedDevice?.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Image: ${uiState.selectedImage?.displayName ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
