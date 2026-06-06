package com.arosys.meetingassistant.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-scoped event bus held by [com.arosys.meetingassistant.MeetingAssistantApp].
 *
 * Producers:
 *   - [TranscriptionService]  → [pendingTranslations]
 *   - [TranslationService]    → [translationCompleted]
 *   - [TTSService]            → [bluetoothConnected]
 *
 * Consumers:
 *   - [TranslationService]    ← [pendingTranslations]
 *   - [TTSService]            ← [translationCompleted], [currentMeetingId]
 *   - LiveMeetingViewModel    ← [bluetoothConnected], [currentMeetingId]
 */
class MeetingSession {

    // -------------------------------------------------------------------------
    // Meeting lifecycle
    // -------------------------------------------------------------------------

    private val _currentMeetingId = MutableStateFlow<Long?>(null)
    val currentMeetingId = _currentMeetingId.asStateFlow()

    fun setMeetingId(id: Long?) {
        _currentMeetingId.value = id
    }

    // -------------------------------------------------------------------------
    // Transcription → Translation pipeline
    // -------------------------------------------------------------------------

    private val _pendingTranslations = MutableSharedFlow<PendingTranslation>(extraBufferCapacity = 64)
    val pendingTranslations = _pendingTranslations.asSharedFlow()

    suspend fun submitForTranslation(item: PendingTranslation) {
        _pendingTranslations.emit(item)
    }

    // -------------------------------------------------------------------------
    // Translation → TTS pipeline
    // -------------------------------------------------------------------------

    private val _translationCompleted = MutableSharedFlow<CompletedTranslation>(extraBufferCapacity = 64)
    val translationCompleted = _translationCompleted.asSharedFlow()

    suspend fun notifyTranslationCompleted(item: CompletedTranslation) {
        _translationCompleted.emit(item)
    }

    // -------------------------------------------------------------------------
    // Bluetooth status  (updated by TTSService, read by ViewModel)
    // -------------------------------------------------------------------------

    private val _bluetoothConnected = MutableStateFlow(false)
    val bluetoothConnected = _bluetoothConnected.asStateFlow()

    fun setBluetoothConnected(connected: Boolean) {
        _bluetoothConnected.value = connected
    }
}

// -------------------------------------------------------------------------
// Data classes
// -------------------------------------------------------------------------

data class PendingTranslation(
    val entryId: Long,
    val spanishText: String,
    val meetingId: Long,
)

data class CompletedTranslation(
    val entryId: Long,
    val spanishText: String,
    val englishText: String,
    val meetingId: Long,
)
