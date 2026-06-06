package com.arosys.meetingassistant.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arosys.meetingassistant.ui.screens.LiveMeetingScreen
import com.arosys.meetingassistant.ui.theme.MeetingAssistantTheme
import com.arosys.meetingassistant.ui.viewmodel.LiveMeetingViewModel

class MainActivity : ComponentActivity() {

    private lateinit var meetingViewModel: LiveMeetingViewModel

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        meetingViewModel.onMicPermissionResult(granted)
        if (granted) meetingViewModel.startMeeting()
    }

    // API 31+ requires BLUETOOTH_CONNECT at runtime to discover SCO devices.
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* BT permission result — TTSService will retry connect on next session start */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeetingAssistantTheme {
                meetingViewModel = viewModel()
                LiveMeetingScreen(
                    onRequestMicPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestBluetoothPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    },
                    viewModel = meetingViewModel,
                )
            }
        }

        // Request BLUETOOTH_CONNECT proactively so TTSService can use SCO immediately.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val btGranted = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!btGranted) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }
}
