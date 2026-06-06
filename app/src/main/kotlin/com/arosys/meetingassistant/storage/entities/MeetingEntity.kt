package com.arosys.meetingassistant.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arosys.meetingassistant.core.interfaces.MeetingRecord

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val audioPath: String? = null,
) {
    fun toRecord() = MeetingRecord(id, title, startedAt, endedAt, audioPath)

    companion object {
        fun from(record: MeetingRecord) = MeetingEntity(
            id = record.id,
            title = record.title,
            startedAt = record.startedAt,
            endedAt = record.endedAt,
            audioPath = record.audioPath,
        )
    }
}
