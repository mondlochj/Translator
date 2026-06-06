package com.arosys.meetingassistant.core.interfaces

import kotlinx.coroutines.flow.Flow

interface StorageProvider {

    // -------------------------------------------------------------------------
    // Meetings
    // -------------------------------------------------------------------------

    suspend fun createMeeting(title: String, startedAt: Long = System.currentTimeMillis()): Long
    suspend fun finalizeMeeting(meetingId: Long, endedAt: Long = System.currentTimeMillis())
    suspend fun getMeeting(meetingId: Long): MeetingRecord?
    fun observeAllMeetings(): Flow<List<MeetingRecord>>

    // -------------------------------------------------------------------------
    // Transcript
    // -------------------------------------------------------------------------

    suspend fun saveTranscriptEntry(entry: TranscriptEntry): Long
    fun observeTranscript(meetingId: Long): Flow<List<TranscriptEntry>>
    suspend fun getTranscript(meetingId: Long): List<TranscriptEntry>

    /** Updates the English translation for an existing transcript entry (Phase 2+). */
    suspend fun updateTranslation(entryId: Long, englishText: String)

    // -------------------------------------------------------------------------
    // Analysis (Phase 4+)
    // -------------------------------------------------------------------------

    suspend fun saveMeetingAnalysis(analysis: MeetingAnalysis)
    suspend fun getMeetingAnalysis(meetingId: Long): MeetingAnalysis?

    // -------------------------------------------------------------------------
    // Search (Phase 7)
    // -------------------------------------------------------------------------

    suspend fun search(query: String): List<SearchResult>
}

data class MeetingRecord(
    val id: Long = 0,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val audioPath: String? = null,
)

data class TranscriptEntry(
    val id: Long = 0,
    val meetingId: Long,
    val timestampMs: Long,
    val spanishText: String,
    val englishText: String? = null,
    val isFinal: Boolean = true,
)

data class MeetingAnalysis(
    val meetingId: Long,
    val summary: List<String>,
    val actionItems: List<ActionItem>,
    val decisions: List<String>,
    val risks: List<String>,
    val openQuestions: List<String>,
    val generatedAt: Long = System.currentTimeMillis(),
)

data class ActionItem(
    val description: String,
    val assignee: String? = null,
    val deadline: String? = null,
)

data class SearchResult(
    val meetingId: Long,
    val meetingTitle: String,
    val snippet: String,
    val relevanceScore: Float,
)
