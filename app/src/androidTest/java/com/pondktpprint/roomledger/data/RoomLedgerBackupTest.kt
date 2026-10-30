package com.pondktpprint.roomledger.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.domain.ReadingDraft
import com.pondktpprint.roomledger.domain.RoomRecord
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

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

    private fun legacyBackup(db: RoomLedgerDatabase): ByteArray {
        val oldTables = listOf("rooms", "meter_readings", "bills", "payments", "payment_allocations", "debt_carry_forwards")
        val columns = JSONObject()
        val tables = JSONObject()
        for (table in oldTables) {
            val names = db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }.filterNot { table == "rooms" && it == "groupId" }
            columns.put(table, JSONArray(names))
            val rows = JSONArray()
            if (table == "rooms") {
                val values = mapOf("id" to "legacy-room", "name" to "ห้องก่อนมีกลุ่ม", "monthlyRentSatang" to 100_000L,
                    "waterPricePerUnitSatang" to 100L, "electricityPricePerUnitSatang" to 100L,
                    "isActive" to 1L, "createdAtMillis" to 10L, "updatedAtMillis" to 20L)
                val room = JSONObject()
                names.forEach { room.put(it, values[it] ?: 0L) }
                rows.put(room)
            }
            tables.put(table, rows)
        }
        val payload = JSONObject().put("formatVersion", 1).put("databaseVersion", 3)
            .put("applicationId", "com.pondktpprint.roomledger").put("databaseName", "room-ledger.db")
            .put("createdAtMillis", 123L).put("columns", columns).put("tables", tables).put("appSettings", JSONObject()).toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        val plain = JSONObject().put("payload", payload).put("sha256", hash).toString().toByteArray()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN).put(byteArrayOf(82, 76, 66, 75)).putInt(1).putInt(250_000).put(salt).put(nonce).array()
        val spec = PBEKeySpec(passphrase, salt, 250_000, 256)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encoded, "AES"), GCMParameterSpec(128, nonce))
        encoded.fill(0)
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain).also { plain.fill(0) }
    }

    @Test fun legacyPreGroupBackupRestoresWithoutLosingRoomsOrCreatingGroups() = runBlocking {
        val target = database()
        try {
            val backup = legacyBackup(target)
            val preview = RoomLedgerBackup.inspect(target, backup, passphrase)
            assertEquals(1, preview.roomCount)
            assertEquals(0, preview.groupCount)
            RoomLedgerBackup.restore(target, backup, passphrase)
            assertEquals("ห้องก่อนมีกลุ่ม", target.roomDao().findById("legacy-room")?.name)
            assertNull(target.roomDao().findById("legacy-room")?.groupId)
            assertTrue(target.roomGroupDao().observeAll().first().isEmpty())
        } finally { target.close() }
    }

    @Test fun encryptedBackupRoundTripsRoomsBillsPaymentsAllocationsAndCarryLedger() = runBlocking {
        val (source, sourceRepo) = populatedDatabase()
        val target = database()
        try {
            val roomId = source.openHelper.writableDatabase.query("SELECT id FROM rooms").use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
            val group = sourceRepo.createRoomGroup("อาคาร A", 10)
            sourceRepo.assignRoomToGroup(roomId, group.id, 11)
            val backup = export(source)
            val preview = RoomLedgerBackup.inspect(target, ByteArrayInputStream(backup), passphrase)
            assertEquals(1, preview.roomCount)
            assertEquals(1, preview.groupCount)
            assertEquals(1, preview.billCount)
            assertEquals(3, preview.paymentCount)
            RoomLedgerBackup.restore(target, backup, passphrase)
            assertEquals("ห้องสำรอง", target.roomDao().findById(roomId)?.name)
            assertEquals(group.id, target.roomDao().findById(roomId)?.groupId)
            assertEquals("อาคาร A", target.roomGroupDao().findById(group.id)?.name)
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
