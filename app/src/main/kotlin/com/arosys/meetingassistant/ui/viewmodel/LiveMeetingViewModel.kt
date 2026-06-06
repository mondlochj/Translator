package com.arosys.meetingassistant.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arosys.meetingassistant.MeetingAssistantApp
import com.arosys.meetingassistant.accelerator.AcceleratorState
import com.arosys.meetingassistant.accelerator.HardwareAcceleratorManager
import com.arosys.meetingassistant.core.interfaces.AudioMode
import com.arosys.meetingassistant.core.interfaces.TranscriptEntry
import com.arosys.meetingassistant.services.SessionState
import com.arosys.meetingassistant.services.TranscriptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "LiveMeetingViewModel"

data class LiveMeetingUiState(
    val sessionState: SessionState = SessionState.IDLE,
    val modelStatus: String = "Not loaded",
    val backendLabel: String = "",
    val transcriptEntries: List<TranscriptEntry> = emptyList(),
    val partialText: String = "",
    val currentMeetingId: Long? = null,
    val acceleratorStatus: String = "",
    val micPermissionGranted: Boolean = false,
    val audioMode: AudioMode = AudioMode.ALL_SPEECH,
    val bluetoothConnected: Boolean = false,
)

class LiveMeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<MeetingAssistantApp>()

    private val _permissionGranted = MutableStateFlow(false)
    private val _serviceState = MutableStateFlow(
        com.arosys.meetingassistant.services.TranscriptionState()
    )

    private var transcriptionService: TranscriptionService? = null

    val uiState: StateFlow<LiveMeetingUiState> = combine(
        _serviceState,
        _permissionGranted,
        HardwareAcceleratorManager.instance.state,
        app.meetingSession.bluetoothConnected,
        app.userPreferences.audioMode,
    ) { svc, perm, accel, btConnected, mode ->
        LiveMeetingUiState(
            sessionState         = svc.sessionState,
            modelStatus          = svc.modelStatus,
            backendLabel         = svc.backendLabel,
            transcriptEntries    = svc.transcriptEntries,
            partialText          = svc.partialText,
            currentMeetingId     = svc.currentMeetingId,
            acceleratorStatus    = accel.toStatusLabel(),
            micPermissionGranted = perm,
            audioMode            = mode,
            bluetoothConnected   = btConnected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveMeetingUiState(),
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TranscriptionService.LocalBinder ?: return
            transcriptionService = binder.service
            viewModelScope.launch {
                binder.service.uiState.collect { _serviceState.value = it }
            }
            Log.d(TAG, "TranscriptionService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transcriptionService = null
            Log.d(TAG, "TranscriptionService disconnected")
        }
    }

    init {
        bindToService()
    }

    // -------------------------------------------------------------------------
    // Public actions
    // -------------------------------------------------------------------------

    fun onMicPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
    }

    fun startMeeting() {
        if (!_permissionGranted.value) return
        transcriptionService?.startSession() ?: Log.w(TAG, "Service not bound yet")
    }

    fun stopMeeting() {
        transcriptionService?.stopSession()
    }

    fun setAudioMode(mode: AudioMode) {
        viewModelScope.launch { app.userPreferences.setAudioMode(mode) }
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        viewModelScope.launch { app.userPreferences.setBluetoothEnabled(enabled) }
    }

    // -------------------------------------------------------------------------

    private fun bindToService() {
        val intent = Intent(getApplication(), TranscriptionService::class.java)
        getApplication<Application>().bindService(
            intent, serviceConnection, Context.BIND_AUTO_CREATE,
        )
    }

    override fun onCleared() {
        super.onCleared()
        try { getApplication<Application>().unbindService(serviceConnection) }
        catch (_: Exception) { }
    }

    private fun AcceleratorState.toStatusLabel(): String =
        "${defaultBackend.displayName} · ${deviceInfo?.socModel ?: "Unknown"}"
}
