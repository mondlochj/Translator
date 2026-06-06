package com.arosys.meetingassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arosys.meetingassistant.core.interfaces.AudioMode
import com.arosys.meetingassistant.services.SessionState
import com.arosys.meetingassistant.ui.components.AudioModeChip
import com.arosys.meetingassistant.ui.components.TranscriptItem
import com.arosys.meetingassistant.ui.theme.PartialTextColor
import com.arosys.meetingassistant.ui.theme.RecordingRed
import com.arosys.meetingassistant.ui.viewmodel.LiveMeetingViewModel

@Composable
fun LiveMeetingScreen(
    onRequestMicPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit = {},
    viewModel: LiveMeetingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.transcriptEntries.size) {
        if (state.transcriptEntries.isNotEmpty()) {
            listState.animateScrollToItem(state.transcriptEntries.size - 1)
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ----------------------------------------------------------------
            // Status bar
            // ----------------------------------------------------------------
            StatusBar(
                sessionState       = state.sessionState,
                modelStatus        = state.modelStatus,
                backendLabel       = state.backendLabel,
                acceleratorStatus  = state.acceleratorStatus,
                audioMode          = state.audioMode,
                bluetoothConnected = state.bluetoothConnected,
                onSettingsClick    = { showSettings = true },
            )

            // ----------------------------------------------------------------
            // Transcript
            // ----------------------------------------------------------------
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.transcriptEntries.isEmpty() && state.partialText.isEmpty()) {
                    Text(
                        text = if (state.sessionState == SessionState.IDLE)
                            "Tap Start to begin transcribing"
                        else
                            "Listening…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.transcriptEntries, key = { it.id }) { entry ->
                        TranscriptItem(entry)
                    }
                    if (state.partialText.isNotEmpty()) {
                        item {
                            Text(
                                text = state.partialText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PartialTextColor,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // ----------------------------------------------------------------
            // Controls
            // ----------------------------------------------------------------
            ControlBar(
                sessionState         = state.sessionState,
                micPermissionGranted = state.micPermissionGranted,
                onStart = {
                    if (!state.micPermissionGranted) onRequestMicPermission()
                    else viewModel.startMeeting()
                },
                onStop = viewModel::stopMeeting,
            )
        }
    }

    // Settings bottom sheet
    if (showSettings) {
        SettingsBottomSheet(
            currentMode       = state.audioMode,
            bluetoothEnabled  = true,
            onModeChange      = { viewModel.setAudioMode(it) },
            onBluetoothToggle = { viewModel.setBluetoothEnabled(it) },
            onDismiss         = { showSettings = false },
        )
    }
}

@Composable
private fun StatusBar(
    sessionState: SessionState,
    modelStatus: String,
    backendLabel: String,
    acceleratorStatus: String,
    audioMode: AudioMode,
    bluetoothConnected: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Recording pulse indicator
        val indicatorColor = when (sessionState) {
            SessionState.RECORDING -> RecordingRed
            SessionState.STARTING  -> Color(0xFFFF9800)
            else                   -> Color(0xFF555555)
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = sessionState.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (backendLabel.isNotEmpty()) "$modelStatus · $backendLabel" else modelStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }

        // Mode + BT chip
        AudioModeChip(
            mode = audioMode,
            bluetoothConnected = bluetoothConnected,
            onClick = onSettingsClick,
        )

        // Accelerator status
        if (acceleratorStatus.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = acceleratorStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }

        // Settings icon
        IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ControlBar(
    sessionState: SessionState,
    micPermissionGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = sessionState == SessionState.RECORDING
    val isBusy = sessionState == SessionState.STARTING || sessionState == SessionState.STOPPING

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isRecording) {
            Button(
                onClick = onStart,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start")
            }
        } else {
            Button(
                onClick = onStop,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = RecordingRed),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        }

        FilledTonalButton(
            onClick = { /* Phase 7 — export */ },
            enabled = isRecording || sessionState == SessionState.IDLE,
            modifier = Modifier.width(80.dp),
        ) {
            Icon(Icons.Filled.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
        }
    }
}
