package com.pondktpprint.roomledger.domain

data class RoomGroup(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class RoomDeletionResult { DELETED, HAS_HISTORY, NOT_FOUND }

sealed interface RoomGroupFilter {
    data object All : RoomGroupFilter
    data object Ungrouped : RoomGroupFilter
    data class Group(val id: String) : RoomGroupFilter
}

fun filterRoomsByGroup(rooms: List<RoomRecord>, groups: List<RoomGroup>, filter: RoomGroupFilter): List<RoomRecord> = when (filter) {
    RoomGroupFilter.All -> rooms
    RoomGroupFilter.Ungrouped -> rooms.filter { room -> room.groupId == null || groups.none { it.id == room.groupId } }
    is RoomGroupFilter.Group -> rooms.filter { it.groupId == filter.id }
}
