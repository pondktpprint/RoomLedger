package com.pondktpprint.roomledger.data

import com.pondktpprint.roomledger.data.local.RoomDao
import com.pondktpprint.roomledger.data.local.toDomain
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.domain.RoomRecord
import com.pondktpprint.roomledger.domain.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRepositoryImpl(private val roomDao: RoomDao) : RoomRepository {
    override fun observeRooms(): Flow<List<RoomRecord>> =
        roomDao.observeAll().map { rooms -> rooms.map { it.toDomain() } }

    override suspend fun saveRoom(room: RoomRecord) {
        roomDao.upsert(room.toEntity())
    }

    override suspend fun setRoomActive(id: String, isActive: Boolean, updatedAtMillis: Long) {
        roomDao.setActive(id, isActive, updatedAtMillis)
    }
}
