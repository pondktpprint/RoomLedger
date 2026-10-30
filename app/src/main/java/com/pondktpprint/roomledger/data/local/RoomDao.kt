package com.pondktpprint.roomledger.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RoomEntity?

    @Upsert
    suspend fun upsert(room: RoomEntity)

    @Query("UPDATE rooms SET isActive = :isActive, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean, updatedAtMillis: Long)

    @Query("UPDATE rooms SET groupId = :groupId, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun assignGroup(id: String, groupId: String?, updatedAtMillis: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM meter_readings WHERE roomId = :roomId UNION ALL SELECT 1 FROM bills WHERE roomId = :roomId UNION ALL SELECT 1 FROM payments INNER JOIN bills ON bills.id = payments.billId WHERE bills.roomId = :roomId UNION ALL SELECT 1 FROM payment_allocations INNER JOIN bills ON bills.id = payment_allocations.sourceBillId WHERE bills.roomId = :roomId UNION ALL SELECT 1 FROM debt_carry_forwards WHERE roomId = :roomId)")
    suspend fun hasFinancialHistory(roomId: String): Boolean

    @Query("DELETE FROM rooms WHERE id = :id")
    suspend fun deleteIfEmpty(id: String): Int
}
