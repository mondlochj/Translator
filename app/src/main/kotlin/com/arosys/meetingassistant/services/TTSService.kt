package com.arosys.meetingassistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.arosys.meetingassistant.MeetingAssistantApp
import com.arosys.meetingassistant.R
import com.arosys.meetingassistant.core.interfaces.AudioMode
import com.arosys.meetingassistant.core.interfaces.TTSProvider
import com.arosys.meetingassistant.impl.piper.PiperTTSProvider
import com.arosys.meetingassistant.impl.priority.PriorityClassifier
import com.arosys.meetingassistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "TTSService"
private const val NOTIFICATION_ID = 1003
private const val CHANNEL_ID = "tts_channel"

/**
 * Foreground service (type = mediaPlayback) that routes translated English
 * text to the user's Bluetooth earbud.
 *
 * Three modes, configurable in Settings:
 *   A — TEXT_ONLY:    No audio. Service runs but never calls [TTSProvider.speak].
 *   B — ALL_SPEECH:   Speaks every completed translation.
 *   C — PRIORITY_ONLY: Speaks only utterances classified as action items,
 *                       deadlines, financial, questions, or decisions.
 *
 * Bluetooth SCO is pre-connected when a Mode B/C session starts so the first
 * utterance is not clipped by SCO setup latency.
 *
 * The speech channel holds up to [SPEECH_QUEUE_CAPACITY] items and drops the
 * oldest if the meeting pace outstrips TTS speed (prevents falling behind).
 */
class TTSService : Service() {

    inner class LocalBinder : Binder() {
        val service: TTSService get() = this@TTSService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Injectable in tests via property assignment before onStartCommand
    lateinit var tts: TTSProvider
    lateinit var btRouter: BluetoothAudioRouter
    lateinit var userPrefs: UserPreferences

    private var ttsJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    private val speechChannel = Channel<String>(
        capacity = SPEECH_QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _state = MutableStateFlow(TTSState())
    val state: StateFlow<TTSState> = _state.asStateFlow()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        tts       = PiperTTSProvider(applicationContext)
        btRouter  = AndroidBluetoothAudioRouter(applicationContext)
        userPrefs = UserPreferences(applicationContext)
        wakeLock  = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arosys:tts")
            .apply { setReferenceCounted(false) }
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        if (!wakeLock.isHeld) wakeLock.acquire()
        startTts()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        ttsJob?.cancel()
        speechChannel.close()
        if (wakeLock.isHeld) wakeLock.release()
        btRouter.disconnect()
        tts.close()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    // -------------------------------------------------------------------------
    // TTS pipeline
    // -------------------------------------------------------------------------

    private fun startTts() {
        if (ttsJob?.isActive == true) return

        ttsJob = scope.launch {
            // Warm up TTS engine
            if (!tts.isReady) {
                _state.value = _state.value.copy(ttsStatus = "Loading TTS…")
                tts.warmUp()
                _state.value = _state.value.copy(
                    ttsStatus = if (tts.isReady) "TTS ready" else "TTS unavailable"
                )
            }

            val session = (application as MeetingAssistantApp).meetingSession

            // Track current mode reactively so settings changes take effect immediately
            var currentMode = AudioMode.ALL_SPEECH
            launch {
                userPrefs.audioMode.collect { mode ->
                    currentMode = mode
                    _state.value = _state.value.copy(audioMode = mode)
                    updateNotification()
                }
            }

            // Pre-connect Bluetooth for audio modes; watch for mode switches
            launch {
                userPrefs.audioMode.collect { mode ->
                    val btEnabled = userPrefs.bluetoothEnabled.first()
                    if (mode != AudioMode.TEXT_ONLY && btEnabled) {
                        if (!btRouter.isConnected.value) {
                            val connected = btRouter.connect()
                            session.setBluetoothConnected(connected)
                            _state.value = _state.value.copy(bluetoothConnected = connected)
                            Log.i(TAG, "BT pre-connect: $connected")
                        }
                    } else if (mode == AudioMode.TEXT_ONLY) {
                        btRouter.disconnect()
                        session.setBluetoothConnected(false)
                        _state.value = _state.value.copy(bluetoothConnected = false)
                    }
                }
            }

            // Speech dispatch loop — serialises utterances so they don't overlap
            launch {
                for (text in speechChannel) {
                    if (!tts.isReady) continue
                    _state.value = _state.value.copy(isSpeaking = true)
                    tts.speak(text)
                    _state.value = _state.value.copy(isSpeaking = false)
                }
            }

            // Stop self when session ends (meeting ID cleared by TranscriptionService)
            launch {
                session.currentMeetingId.collect { id ->
                    if (id == null && _state.value.audioMode != AudioMode.TEXT_ONLY) {
                        Log.i(TAG, "Session ended — disconnecting BT and stopping")
                        btRouter.disconnect()
                        session.setBluetoothConnected(false)
                        stopSelf()
                    }
                }
            }

            // Main collection loop: receive completed translations, apply mode filter
            val classifier = PriorityClassifier()
            session.translationCompleted.collect { completed ->
                when (currentMode) {
                    AudioMode.TEXT_ONLY -> { /* no speech */ }
                    AudioMode.ALL_SPEECH -> {
                        speechChannel.trySend(completed.englishText)
                    }
                    AudioMode.PRIORITY_ONLY -> {
                        val event = classifier.classify(completed.spanishText)
                        if (event != null) {
                            Log.d(TAG, "Priority [${event.category.label}]: ${completed.spanishText.take(40)}")
                            speechChannel.trySend(completed.englishText)
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tts_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val modeText = when (_state.value.audioMode) {
            AudioMode.TEXT_ONLY     -> getString(R.string.tts_notification_text_a)
            AudioMode.ALL_SPEECH    -> getString(R.string.tts_notification_text_b)
            AudioMode.PRIORITY_ONLY -> getString(R.string.tts_notification_text_c)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tts_notification_title))
            .setContentText(modeText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val SPEECH_QUEUE_CAPACITY = 3
    }
}

// -------------------------------------------------------------------------
// State
// -------------------------------------------------------------------------

data class TTSState(
    val ttsStatus: String = "Not loaded",
    val bluetoothConnected: Boolean = false,
    val isSpeaking: Boolean = false,
    val audioMode: AudioMode = AudioMode.ALL_SPEECH,
)
