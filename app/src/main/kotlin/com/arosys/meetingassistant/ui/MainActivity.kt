package com.arosys.meetingassistant.ui

import android.Manifest
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeetingAssistantTheme {
                meetingViewModel = viewModel()
                LiveMeetingScreen(
                    onRequestMicPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    viewModel = meetingViewModel,
                )
            }
        }

        // Report current permission state on startup
        val alreadyGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        // ViewModel isn't ready in onCreate — deferred via LaunchedEffect or first composition
    }
}
