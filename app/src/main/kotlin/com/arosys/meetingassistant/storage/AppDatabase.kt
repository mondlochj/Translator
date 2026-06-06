package com.arosys.meetingassistant.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.arosys.meetingassistant.storage.entities.MeetingEntity
import com.arosys.meetingassistant.storage.entities.TranscriptEntryEntity

@Database(
    entities = [MeetingEntity::class, TranscriptEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun meetingDao(): MeetingDao
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meeting_assistant.db",
                ).build().also { instance = it }
            }

        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
