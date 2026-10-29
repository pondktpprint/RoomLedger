package com.pondktpprint.roomledger.domain

import kotlinx.coroutines.flow.Flow

/** Storage boundary; a future backup implementation can be added without coupling the UI to Firebase. */
interface RoomRepository {
    fun observeRooms(): Flow<List<RoomRecord>>
    suspend fun saveRoom(room: RoomRecord)
    suspend fun setRoomActive(id: String, isActive: Boolean, updatedAtMillis: Long)
}
