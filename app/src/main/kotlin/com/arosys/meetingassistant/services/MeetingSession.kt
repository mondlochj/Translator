package com.arosys.meetingassistant.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-scoped bus shared by [TranscriptionService] and [TranslationService].
 *
 * Avoids direct coupling between the two services:
 *   - [TranscriptionService] writes to [pendingTranslations]
 *   - [TranslationService]   reads from [pendingTranslations],
 *     translates, and calls back into Room via [StorageProvider]
 *
 * Held as a singleton in [com.arosys.meetingassistant.MeetingAssistantApp].
 */
class MeetingSession {

    private val _currentMeetingId = MutableStateFlow<Long?>(null)
    val currentMeetingId = _currentMeetingId.asStateFlow()

    /** Published by [TranscriptionService] for each final segment. */
    private val _pendingTranslations = MutableSharedFlow<PendingTranslation>(extraBufferCapacity = 64)
    val pendingTranslations = _pendingTranslations.asSharedFlow()

    fun setMeetingId(id: Long?) { _currentMeetingId.value = id }

    suspend fun submitForTranslation(item: PendingTranslation) {
        _pendingTranslations.emit(item)
    }
}

data class PendingTranslation(
    val entryId: Long,
    val spanishText: String,
    val meetingId: Long,
)
