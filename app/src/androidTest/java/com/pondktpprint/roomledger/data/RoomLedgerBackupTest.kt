package com.pondktpprint.roomledger.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.domain.ReadingDraft
import com.pondktpprint.roomledger.domain.RoomRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class RoomLedgerBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val passphrase = "secure-test-passphrase".toCharArray()

    private fun database() = Room.inMemoryDatabaseBuilder(context, RoomLedgerDatabase::class.java).allowMainThreadQueries().build()

    private suspend fun populatedDatabase(): Pair<RoomLedgerDatabase, RoomRepositoryImpl> {
        val db = database()
        val room = RoomRecord(UUID.randomUUID().toString(), "ห้องสำรอง", 150_000, 120, 80, true, 1, 2)
        db.roomDao().upsert(room.toEntity())
        val repo = RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
        val bill = repo.saveReading(room, ReadingDraft(room.id, "2026-10", 10, 15, 0, 20, 30, 0), false, 3)
        repo.recordPayment(bill.id, 20_000, 4, "ทดสอบรับบางส่วน", 5)
        val secondPayment = repo.recordPayment(bill.id, 5_000, 6, "รายการที่จะกลับ", 7)
        repo.reversePayment(secondPayment.id, "ทดสอบ audit", 8)
        repo.closeBill(bill.id, 9)
        return db to repo
    }

    private suspend fun export(db: RoomLedgerDatabase): ByteArray {
        val output = ByteArrayOutputStream()
        RoomLedgerBackup.write(db, output, passphrase, nowMillis = 123_456)
        return output.toByteArray()
    }

    @Test fun encryptedBackupRoundTripsRoomsBillsPaymentsAllocationsAndCarryLedger() = runBlocking {
        val (source, sourceRepo) = populatedDatabase()
        val target = database()
        try {
            val roomId = source.openHelper.writableDatabase.query("SELECT id FROM rooms").use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
            val backup = export(source)
            val preview = RoomLedgerBackup.inspect(target, ByteArrayInputStream(backup), passphrase)
            assertEquals(1, preview.roomCount)
            assertEquals(1, preview.billCount)
            assertEquals(3, preview.paymentCount)
            RoomLedgerBackup.restore(target, backup, passphrase)
            assertEquals("ห้องสำรอง", target.roomDao().findById(roomId)?.name)
            val restoredBill = target.billingDao().findBillForRoomMonth(roomId, "2026-10")!!
            assertEquals(sourceRepo.financialSummary(restoredBill.id).outstandingSatang, RoomRepositoryImpl(target.roomDao(), target.billingDao(), target).financialSummary(restoredBill.id).outstandingSatang)
            val restoredPayments = target.billingDao().paymentsForBill(restoredBill.id)
            assertEquals(3, restoredPayments.size)
            var restoredAllocationCount = 0
            for (payment in restoredPayments) restoredAllocationCount += target.billingDao().allocationsForPayment(payment.id).size
            assertEquals(3, restoredAllocationCount)
            assertEquals(1, target.billingDao().pendingCarries(roomId, "2026-11").size)
        } finally { source.close(); target.close() }
    }

    @Test fun wrongPassphraseAndCorruptFileLeaveDatabaseUntouched() = runBlocking {
        val (source, _) = populatedDatabase()
        val target = database()
        val oldRoom = RoomRecord("existing-room", "ข้อมูลเดิม", 100, 1, 1, true, 7, 8)
        try {
            target.roomDao().upsert(oldRoom.toEntity())
            val backup = export(source)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { RoomLedgerBackup.inspect(target, ByteArrayInputStream(backup), "wrong-password".toCharArray()) } }
            val corrupted = backup.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { RoomLedgerBackup.inspect(target, ByteArrayInputStream(corrupted), passphrase) } }
            assertEquals("ข้อมูลเดิม", target.roomDao().findById(oldRoom.id)?.name)
        } finally { source.close(); target.close() }
    }

    @Test fun interruptedReplacementRollsBackAndPreservesCurrentRecords() = runBlocking {
        val (source, _) = populatedDatabase()
        val target = database()
        val oldRoom = RoomRecord("preserved-room", "ห้องเดิม", 100, 1, 1, true, 1, 1)
        try {
            target.roomDao().upsert(oldRoom.toEntity())
            val backup = export(source)
            val importedRoomId = source.openHelper.writableDatabase.query("SELECT id FROM rooms").use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
            target.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_room_restore BEFORE INSERT ON rooms BEGIN SELECT RAISE(ABORT, 'simulated interrupted restore'); END")
            assertThrows(Exception::class.java) { runBlocking { RoomLedgerBackup.restore(target, backup, passphrase) } }
            assertEquals("ห้องเดิม", target.roomDao().findById(oldRoom.id)?.name)
            assertTrue(target.billingDao().history(oldRoom.id).isEmpty())
            assertNull(target.roomDao().findById(importedRoomId))
        } finally { source.close(); target.close() }
    }

    @Test fun passphrasePolicyRejectsShortSecrets() = runBlocking {
        val (db, _) = populatedDatabase()
        try {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { RoomLedgerBackup.write(db, ByteArrayOutputStream(), "short".toCharArray()) } }
        } finally { db.close() }
    }
}
