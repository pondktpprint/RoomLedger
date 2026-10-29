package com.pondktpprint.roomledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: RoomEntity)

    @Query("UPDATE rooms SET isActive = :isActive, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean, updatedAtMillis: Long)
}
