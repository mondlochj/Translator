package com.arosys.meetingassistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.arosys.meetingassistant.MeetingAssistantApp
import com.arosys.meetingassistant.core.interfaces.TranslationEngine
import com.arosys.meetingassistant.core.interfaces.StorageProvider
import com.arosys.meetingassistant.impl.nllb.NLLBTranslationEngine
import com.arosys.meetingassistant.storage.AppDatabase
import com.arosys.meetingassistant.storage.RoomStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "TranslationService"
private const val NOTIFICATION_ID = 1002
private const val CHANNEL_ID = "translation_channel"

/**
 * Foreground service that owns the NLLB model and translates incoming segments.
 *
 * Subscribes to [MeetingSession.pendingTranslations], translates each segment,
 * and writes the English result back to Room via [StorageProvider.updateTranslation].
 * The Room update automatically propagates to the UI through its live Flow.
 */
class TranslationService : Service() {

    inner class LocalBinder : Binder() {
        val service: TranslationService get() = this@TranslationService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var engine: TranslationEngine
    lateinit var storage: StorageProvider

    private var translationJob: Job? = null

    private val _state = MutableStateFlow(TranslationState())
    val state: StateFlow<TranslationState> = _state.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        engine  = NLLBTranslationEngine(applicationContext)
        storage = RoomStorageProvider(AppDatabase.getInstance(applicationContext))
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startTranslating()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        translationJob?.cancel()
        engine.close()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------

    private fun startTranslating() {
        if (translationJob?.isActive == true) return

        translationJob = scope.launch {
            if (!engine.isReady) {
                _state.value = _state.value.copy(modelStatus = "Loading NLLB…")
                engine.warmUp()
                _state.value = _state.value.copy(
                    modelStatus = if (engine.isReady) "NLLB ready" else "NLLB model not found"
                )
            }

            val session = (application as MeetingAssistantApp).meetingSession
            session.pendingTranslations.collect { pending ->
                _state.value = _state.value.copy(
                    isTranslating = true,
                    currentText = pending.spanishText.take(40),
                )

                var partialText = ""
                engine.translateStream(pending.spanishText).collect { result ->
                    partialText = result.translatedText
                    if (result.isFinal) {
                        storage.updateTranslation(pending.entryId, result.translatedText)
                        Log.d(TAG, "Translated: \"${pending.spanishText.take(30)}\" → \"${result.translatedText.take(30)}\"")
                    }
                }

                _state.value = _state.value.copy(isTranslating = false, currentText = "")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Translation", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Translating")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setOngoing(true)
            .build()
}

data class TranslationState(
    val modelStatus: String = "Not loaded",
    val isTranslating: Boolean = false,
    val currentText: String = "",
)
