package com.pondktpprint.roomledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomGroupFilterTest {
    private val first = RoomGroup("group-1", "อาคาร 1", 0, 1, 1)
    private val second = RoomGroup("group-2", "อาคาร 2", 1, 1, 1)
    private val rooms = listOf(
        RoomRecord("room-1", "1/1", 100, 1, 1, true, 1, 1, first.id),
        RoomRecord("room-2", "2/1", 100, 1, 1, true, 1, 1, second.id),
        RoomRecord("room-3", "3/1", 100, 1, 1, true, 1, 1),
    )

    @Test fun allGroupsIncludesEveryRoom() {
        assertEquals(rooms, filterRoomsByGroup(rooms, listOf(first, second), RoomGroupFilter.All))
    }

    @Test fun selectingGroupAndUngroupedReturnsOnlyMatchingRooms() {
        assertEquals(listOf(rooms[0]), filterRoomsByGroup(rooms, listOf(first, second), RoomGroupFilter.Group(first.id)))
        assertEquals(listOf(rooms[2]), filterRoomsByGroup(rooms, listOf(first, second), RoomGroupFilter.Ungrouped))
    }

    @Test fun missingGroupReferenceIsSafelyShownAsUngrouped() {
        val orphan = rooms[0].copy(groupId = "removed-group")
        assertEquals(listOf(orphan), filterRoomsByGroup(listOf(orphan), listOf(first), RoomGroupFilter.Ungrouped))
    }
}
