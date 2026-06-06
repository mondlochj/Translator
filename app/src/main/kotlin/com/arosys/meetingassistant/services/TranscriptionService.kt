package com.arosys.meetingassistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.arosys.meetingassistant.MeetingAssistantApp
import com.arosys.meetingassistant.R
import com.arosys.meetingassistant.audio.MicrophoneAudioSource
import com.arosys.meetingassistant.core.interfaces.SpeechRecognizer
import com.arosys.meetingassistant.core.interfaces.StorageProvider
import com.arosys.meetingassistant.core.interfaces.TranscriptEntry
import com.arosys.meetingassistant.core.interfaces.TranscriptSegment
import com.arosys.meetingassistant.impl.whisper.WhisperOnnxSpeechRecognizer
import com.arosys.meetingassistant.storage.AppDatabase
import com.arosys.meetingassistant.storage.RoomStorageProvider
import com.arosys.meetingassistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "TranscriptionService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "transcription_channel"

/**
 * Foreground service that owns the microphone and Whisper inference.
 * Keeps running when the app is backgrounded so the user can pocket the phone
 * during a meeting.
 *
 * Bind to this service from [LiveMeetingViewModel] to observe [uiState] and
 * call [startSession] / [stopSession].
 */
class TranscriptionService : Service() {

    inner class LocalBinder : Binder() {
        val service: TranscriptionService get() = this@TranscriptionService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Injected in onCreate; overrideable in tests via setter
    lateinit var recognizer: SpeechRecognizer
    lateinit var storage: StorageProvider

    private var mic: MicrophoneAudioSource? = null
    private var transcriptionJob: Job? = null

    private val _uiState = MutableStateFlow(TranscriptionState())
    val uiState: StateFlow<TranscriptionState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        recognizer = WhisperOnnxSpeechRecognizer(applicationContext)
        storage = RoomStorageProvider(AppDatabase.getInstance(applicationContext))
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopSession()
        recognizer.close()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Session control
    // -------------------------------------------------------------------------

    fun startSession(meetingTitle: String = "Meeting") {
        if (_uiState.value.sessionState != SessionState.IDLE) return
        _uiState.value = _uiState.value.copy(sessionState = SessionState.STARTING)
        startForeground(NOTIFICATION_ID, buildNotification())

        transcriptionJob = scope.launch {
            try {
                // Warm up model (no-op if already loaded)
                if (!recognizer.isReady) {
                    _uiState.value = _uiState.value.copy(modelStatus = "Loading model…")
                    recognizer.warmUp()
                }
                _uiState.value = _uiState.value.copy(
                    modelStatus = if (recognizer.isReady) "Whisper ready" else "Model not found",
                    backendLabel = "CPU",
                )

                val meetingId = storage.createMeeting(meetingTitle)
                (application as MeetingAssistantApp).meetingSession.setMeetingId(meetingId)

                // Start TranslationService alongside transcription
                startService(Intent(this@TranscriptionService, TranslationService::class.java))

                val micSource = MicrophoneAudioSource().also { mic = it }
                micSource.startRecording()

                _uiState.value = _uiState.value.copy(
                    sessionState = SessionState.RECORDING,
                    currentMeetingId = meetingId,
                    transcriptEntries = emptyList(),
                    partialText = "",
                )
                Log.i(TAG, "Session started, meetingId=$meetingId")

                recognizer.transcribeStream(micSource).collect { segment ->
                    handleSegment(segment, meetingId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                _uiState.value = _uiState.value.copy(sessionState = SessionState.IDLE)
            }
        }
    }

    fun stopSession() {
        if (_uiState.value.sessionState == SessionState.IDLE) return
        _uiState.value = _uiState.value.copy(sessionState = SessionState.STOPPING)

        transcriptionJob?.cancel()
        mic?.stopRecording()
        mic = null

        scope.launch {
            _uiState.value.currentMeetingId?.let { id ->
                storage.finalizeMeeting(id)
            }
            _uiState.value = _uiState.value.copy(sessionState = SessionState.IDLE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.i(TAG, "Session stopped")
        }
    }

    // -------------------------------------------------------------------------
    // Segment handling
    // -------------------------------------------------------------------------

    private suspend fun handleSegment(segment: TranscriptSegment, meetingId: Long) {
        if (!segment.isFinal) {
            _uiState.value = _uiState.value.copy(partialText = segment.text)
            return
        }

        _uiState.value = _uiState.value.copy(partialText = "")
        if (segment.text.isBlank()) return

        val entry = TranscriptEntry(
            meetingId = meetingId,
            timestampMs = segment.startMs,
            spanishText = segment.text,
            isFinal = true,
        )
        val savedId = storage.saveTranscriptEntry(entry)
        val savedEntry = entry.copy(id = savedId)

        _uiState.value = _uiState.value.copy(
            transcriptEntries = _uiState.value.transcriptEntries + savedEntry
        )

        // Dispatch to TranslationService via MeetingSession
        (application as MeetingAssistantApp).meetingSession.submitForTranslation(
            PendingTranslation(savedId, segment.text, meetingId)
        )
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}

// -------------------------------------------------------------------------
// State types
// -------------------------------------------------------------------------

enum class SessionState { IDLE, STARTING, RECORDING, STOPPING }

data class TranscriptionState(
    val sessionState: SessionState = SessionState.IDLE,
    val modelStatus: String = "Not loaded",
    val backendLabel: String = "",
    val currentMeetingId: Long? = null,
    val transcriptEntries: List<TranscriptEntry> = emptyList(),
    val partialText: String = "",
)
