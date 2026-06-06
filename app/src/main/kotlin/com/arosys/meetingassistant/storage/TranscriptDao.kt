package com.arosys.meetingassistant.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arosys.meetingassistant.storage.entities.TranscriptEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranscriptEntryEntity): Long

    @Query("SELECT * FROM transcript_entries WHERE meetingId = :meetingId ORDER BY timestampMs ASC")
    fun observeByMeeting(meetingId: Long): Flow<List<TranscriptEntryEntity>>

    @Query("SELECT * FROM transcript_entries WHERE meetingId = :meetingId ORDER BY timestampMs ASC")
    suspend fun getByMeeting(meetingId: Long): List<TranscriptEntryEntity>

    @Query("UPDATE transcript_entries SET englishText = :english WHERE id = :id")
    suspend fun updateEnglish(id: Long, english: String)

    @Query("""
        SELECT * FROM transcript_entries
        WHERE spanishText LIKE '%' || :query || '%'
        OR englishText LIKE '%' || :query || '%'
        ORDER BY timestampMs DESC
        LIMIT 100
    """)
    suspend fun search(query: String): List<TranscriptEntryEntity>
}
