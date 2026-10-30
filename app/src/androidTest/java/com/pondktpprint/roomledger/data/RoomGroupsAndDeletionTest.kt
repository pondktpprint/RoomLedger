package com.pondktpprint.roomledger.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.domain.ReadingDraft
import com.pondktpprint.roomledger.domain.RoomDeletionResult
import com.pondktpprint.roomledger.domain.RoomRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RoomGroupsAndDeletionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun database() = Room.inMemoryDatabaseBuilder(context, RoomLedgerDatabase::class.java).allowMainThreadQueries().build()

    @Test fun createRenameMoveAndDeleteGroupKeepsRoomRecords() = runBlocking {
        val db = database()
        try {
            val repo = RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
            val room = room("1/6")
            db.roomDao().upsert(room.toEntity())
            val group = repo.createRoomGroup("ตึก A", 10)
            val other = repo.createRoomGroup("ตึก B", 11)
            repo.renameRoomGroup(group.id, "อาคาร A", 12)
            repo.assignRoomToGroup(room.id, group.id, 13)
            assertEquals(group.id, db.roomDao().findById(room.id)?.groupId)
            assertEquals("อาคาร A", db.roomGroupDao().findById(group.id)?.name)
            repo.assignRoomToGroup(room.id, other.id, 14)
            assertEquals(other.id, db.roomDao().findById(room.id)?.groupId)

            repo.deleteRoomGroup(other.id, 15)
            assertNull(db.roomDao().findById(room.id)?.groupId)
            assertEquals(room.name, db.roomDao().findById(room.id)?.name)
            assertEquals(listOf(group.id), db.roomGroupDao().observeAll().first().map { it.id })
        } finally { db.close() }
    }

    @Test fun emptyRoomCanBePermanentlyDeleted() = runBlocking {
        val db = database()
        try {
            val room = room("empty")
            db.roomDao().upsert(room.toEntity())
            val repo = RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
            assertFalse(repo.roomHasFinancialHistory(room.id))
            assertEquals(RoomDeletionResult.DELETED, repo.deleteRoomPermanently(room.id))
            assertNull(db.roomDao().findById(room.id))
        } finally { db.close() }
    }

    @Test fun roomWithBillAndPaymentCannotBeDeletedAndDeactivationKeepsHistory() = runBlocking {
        val db = database()
        try {
            val repo = RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
            val room = room("history")
            db.roomDao().upsert(room.toEntity())
            val bill = repo.saveReading(room, ReadingDraft(room.id, "2026-10", 10, 15, 0, 20, 25, 0), false, 3)
            repo.recordPayment(bill.id, 10_000, 4, "จ่ายบางส่วน", 5)
            assertTrue(repo.roomHasFinancialHistory(room.id))
            assertEquals(RoomDeletionResult.HAS_HISTORY, repo.deleteRoomPermanently(room.id))
            repo.setRoomActive(room.id, false, 6)
            assertFalse(db.roomDao().findById(room.id)!!.isActive)
            assertEquals(1, db.billingDao().history(room.id).size)
            assertEquals(1, db.billingDao().paymentsForBill(bill.id).size)
        } finally { db.close() }
    }

    private fun room(name: String) = RoomRecord(UUID.randomUUID().toString(), name, 100_000, 100, 100, true, 1, 1)
}
