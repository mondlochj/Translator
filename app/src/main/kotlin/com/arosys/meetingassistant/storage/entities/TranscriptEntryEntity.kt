package com.arosys.meetingassistant.storage.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arosys.meetingassistant.core.interfaces.TranscriptEntry

@Entity(
    tableName = "transcript_entries",
    foreignKeys = [ForeignKey(
        entity = MeetingEntity::class,
        parentColumns = ["id"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("meetingId")],
)
data class TranscriptEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val timestampMs: Long,
    val spanishText: String,
    val englishText: String? = null,
    val isFinal: Boolean = true,
) {
    fun toEntry() = TranscriptEntry(id, meetingId, timestampMs, spanishText, englishText, isFinal)

    companion object {
        fun from(entry: TranscriptEntry) = TranscriptEntryEntity(
            id = entry.id,
            meetingId = entry.meetingId,
            timestampMs = entry.timestampMs,
            spanishText = entry.spanishText,
            englishText = entry.englishText,
            isFinal = entry.isFinal,
        )
    }
}
