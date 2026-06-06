package com.arosys.meetingassistant.storage

import com.arosys.meetingassistant.core.interfaces.ActionItem
import com.arosys.meetingassistant.core.interfaces.MeetingAnalysis
import com.arosys.meetingassistant.core.interfaces.MeetingRecord
import com.arosys.meetingassistant.core.interfaces.SearchResult
import com.arosys.meetingassistant.core.interfaces.StorageProvider
import com.arosys.meetingassistant.core.interfaces.TranscriptEntry
import com.arosys.meetingassistant.storage.entities.MeetingEntity
import com.arosys.meetingassistant.storage.entities.TranscriptEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStorageProvider(private val db: AppDatabase) : StorageProvider {

    private val meetingDao = db.meetingDao()
    private val transcriptDao = db.transcriptDao()

    override suspend fun createMeeting(title: String, startedAt: Long): Long =
        meetingDao.insert(MeetingEntity(title = title, startedAt = startedAt))

    override suspend fun finalizeMeeting(meetingId: Long, endedAt: Long) =
        meetingDao.finalize(meetingId, endedAt)

    override suspend fun getMeeting(meetingId: Long): MeetingRecord? =
        meetingDao.getById(meetingId)?.toRecord()

    override fun observeAllMeetings(): Flow<List<MeetingRecord>> =
        meetingDao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun saveTranscriptEntry(entry: TranscriptEntry): Long =
        transcriptDao.insert(TranscriptEntryEntity.from(entry))

    override fun observeTranscript(meetingId: Long): Flow<List<TranscriptEntry>> =
        transcriptDao.observeByMeeting(meetingId).map { list -> list.map { it.toEntry() } }

    override suspend fun getTranscript(meetingId: Long): List<TranscriptEntry> =
        transcriptDao.getByMeeting(meetingId).map { it.toEntry() }

    override suspend fun updateTranslation(entryId: Long, englishText: String) =
        transcriptDao.updateEnglish(entryId, englishText)

    override suspend fun saveMeetingAnalysis(analysis: MeetingAnalysis) {
        // Phase 4 — entity and DAO will be added then
    }

    override suspend fun getMeetingAnalysis(meetingId: Long): MeetingAnalysis? = null

    override suspend fun search(query: String): List<SearchResult> {
        val entries = transcriptDao.search(query)
        return entries.mapNotNull { entity ->
            val meeting = meetingDao.getById(entity.meetingId) ?: return@mapNotNull null
            SearchResult(
                meetingId = entity.meetingId,
                meetingTitle = meeting.title,
                snippet = entity.englishText ?: entity.spanishText,
                relevanceScore = 1.0f,
            )
        }
    }
}
