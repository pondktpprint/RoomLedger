package com.pondktpprint.roomledger.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pondktpprint.roomledger.domain.RoomGroup

@Entity(tableName = "room_groups", indices = [Index(value = ["sortOrder", "name"])])
data class RoomGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

fun RoomGroupEntity.toDomain() = RoomGroup(id, name, sortOrder, createdAtMillis, updatedAtMillis)
