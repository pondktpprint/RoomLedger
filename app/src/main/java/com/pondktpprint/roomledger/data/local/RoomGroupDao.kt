package com.pondktpprint.roomledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomGroupDao {
    @Query("SELECT * FROM room_groups ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<RoomGroupEntity>>

    @Query("SELECT * FROM room_groups WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RoomGroupEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM room_groups")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: RoomGroupEntity)

    @Query("UPDATE room_groups SET name = :name, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAtMillis: Long): Int

    @Query("UPDATE rooms SET groupId = NULL, updatedAtMillis = :updatedAtMillis WHERE groupId = :groupId")
    suspend fun ungroupRooms(groupId: String, updatedAtMillis: Long)

    @Query("DELETE FROM room_groups WHERE id = :id")
    suspend fun delete(id: String): Int
}
