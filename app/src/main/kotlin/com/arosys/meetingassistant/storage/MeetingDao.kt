package com.arosys.meetingassistant.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arosys.meetingassistant.storage.entities.MeetingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MeetingEntity): Long

    @Update
    suspend fun update(entity: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getById(id: Long): MeetingEntity?

    @Query("SELECT * FROM meetings ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<MeetingEntity>>

    @Query("UPDATE meetings SET endedAt = :endedAt WHERE id = :id")
    suspend fun finalize(id: Long, endedAt: Long)
}
