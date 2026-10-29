package com.pondktpprint.roomledger.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.domain.PaymentState
import com.pondktpprint.roomledger.domain.ReadingDraft
import com.pondktpprint.roomledger.domain.RoomRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class RoomRepositoryWorkflowTest {
    @Test fun firstReadingSeedsNextMonthAndPhaseTwoBillsSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "phase25-${UUID.randomUUID()}.db"
        fun openDb() = Room.databaseBuilder(context, RoomLedgerDatabase::class.java, databaseName)
            .addMigrations(RoomLedgerDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            var db = openDb()
            var repository = RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
            val room = RoomRecord("room-phase25", "ห้องทดสอบ", 700_000, 200, 500, true, 1, 1)
            db.roomDao().upsert(room.toEntity())

            val january = ReadingDraft(room.id, "2026-01", 100, 120, 0, 300, 350, 0)
            repository.saveReading(room, january, false, 2)
            val suggestion = repository.priorReading(room.id, "2026-02")!!
            assertEquals(120L, suggestion.waterPrevious)
            assertEquals(350L, suggestion.electricityPrevious)

            val february = ReadingDraft(room.id, "2026-02", suggestion.waterPrevious, 135, 0, suggestion.electricityPrevious, 365, 0)
            val firstBill = repository.saveReading(room, february, false, 3)
            assertEquals(15L, firstBill.waterUnits)
            assertEquals(15L, firstBill.electricityUnits)
            assertEquals(710_500L, firstBill.totalSatang)
            repository.saveReading(room, february.copy(waterCurrent = 140), false, 4)
            repository.saveReading(room.copy(monthlyRentSatang = 999_000, waterPricePerUnitSatang = 99_000, electricityPricePerUnitSatang = 88_000), february.copy(waterCurrent = 140), false, 5)
            val edited = repository.billHistory(room.id).first { it.billingMonth == "2026-02" }
            assertEquals(20L, edited.waterUnits)
            assertEquals(700_000L, edited.rentSatang)
            assertEquals(200L, edited.waterRateSatang)
            assertEquals(500L, edited.electricityRateSatang)
            assertEquals(1, repository.billHistory(room.id).count { it.billingMonth == "2026-02" })

            repository.reconcilePayment(edited, PaymentState.PAID, 6)
            db.close()
            db = openDb()
            repository = RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
            val reopened = repository.billHistory(room.id).first { it.billingMonth == "2026-02" }
            assertEquals(edited.totalSatang, reopened.totalSatang)
            assertEquals(PaymentState.PAID, reopened.paymentState)
            assertEquals("ห้องทดสอบ", reopened.roomName)
            assertEquals("ห้องทดสอบ", db.roomDao().findById(room.id)?.name)
            assertEquals(140L, repository.readingForMonth(room.id, "2026-02")?.waterCurrent)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun paidTotalMustBeReconciledBeforeEditing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RoomLedgerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
            val room = RoomRecord("paid-room", "ห้องชำระ", 500_000, 100, 100, true, 1, 1)
            db.roomDao().upsert(room.toEntity())
            val saved = repository.saveReading(room, ReadingDraft(room.id, "2026-03", 10, 12, 0, 20, 22, 0), false, 2)
            repository.reconcilePayment(saved, PaymentState.PAID, 3)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.saveReading(room, ReadingDraft(room.id, "2026-03", 10, 13, 0, 20, 22, 0), false, 4) }
            }
            val stillPaid = repository.billHistory(room.id).single()
            assertEquals(PaymentState.PAID, stillPaid.paymentState)
            assertEquals(saved.totalSatang, stillPaid.totalSatang)
        } finally { db.close() }
    }
}
