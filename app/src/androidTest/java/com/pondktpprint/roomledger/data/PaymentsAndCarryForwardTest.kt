package com.pondktpprint.roomledger.data

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.domain.*
import com.pondktpprint.roomledger.presentation.BillImageExport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PaymentsAndCarryForwardTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val room = RoomRecord("phase3-${UUID.randomUUID()}", "ห้อง 301", 10_000, 100, 100, true, 1, 1)

    private suspend fun database(): Pair<RoomLedgerDatabase, RoomRepositoryImpl> {
        val db = Room.inMemoryDatabaseBuilder(context, RoomLedgerDatabase::class.java).allowMainThreadQueries().build()
        db.roomDao().upsert(room.toEntity())
        return db to RoomRepositoryImpl(db.roomDao(), db.billingDao(), db)
    }

    private suspend fun bill(repo: RoomRepositoryImpl, month: String) = repo.saveReading(room, ReadingDraft(room.id, month, 0, 0, 0, 0, 0, 0), false, 1)

    @Test fun tracksMultiplePartialPaymentsAndReversalWithoutOverwritingHistory() = runBlocking {
        val (db, repo) = database()
        try {
            val bill = bill(repo, "2026-10")
            repo.recordPayment(bill.id, 2_500, 10, "เงินสด", 11)
            val second = repo.recordPayment(bill.id, 3_000, 20, "โอน", 21)
            var summary = repo.financialSummary(bill.id)
            assertEquals(5_500L, summary.totalReceivedSatang)
            assertEquals(4_500L, summary.outstandingSatang)
            repo.reversePayment(second.id, "แก้จำนวนเงินรับ", 30)
            summary = repo.financialSummary(bill.id)
            assertEquals(2_500L, summary.totalReceivedSatang)
            assertEquals(7_500L, summary.outstandingSatang)
            assertEquals(3, summary.payments.size)
            assertEquals(PaymentEntryType.REVERSAL, summary.payments.first().entryType)
        } finally { db.close() }
    }

    @Test fun closesUnpaidAndPartiallyPaidBillsAndCarriesEachDebtOnlyOnce() = runBlocking {
        val (db, repo) = database()
        try {
            val december = bill(repo, "2025-12")
            repo.recordPayment(december.id, 2_000, 10, null, 11)
            repo.closeBill(december.id, 12)
            assertTrue(repo.financialSummary(december.id).isClosed)
            assertEquals(8_000L, repo.financialSummary(december.id).outstandingSatang)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.closeBill(december.id, 13) } }

            val january = bill(repo, "2026-01")
            assertEquals(8_000L, january.carriedDebtSatang)
            assertEquals(18_000L, january.totalDueSatang)
            repo.closeBill(january.id, 20)
            val february = bill(repo, "2026-02")
            assertEquals(18_000L, february.carriedDebtSatang)
            assertEquals(28_000L, february.totalDueSatang)
            repo.saveReading(room, ReadingDraft(room.id, "2026-02", 0, 0, 0, 0, 0, 0), false, 21)
            assertEquals(1, repo.billsForMonth("2026-02").count { it.roomId == room.id })
            assertEquals(18_000L, repo.billForRoomMonth(room.id, "2026-02")!!.carriedDebtSatang)
        } finally { db.close() }
    }

    @Test fun latePaymentReducesTheExistingCarryInsteadOfCreatingDuplicateDebt() = runBlocking {
        val (db, repo) = database()
        try {
            val january = bill(repo, "2026-01")
            repo.closeBill(january.id, 2)
            val february = bill(repo, "2026-02")
            assertEquals(10_000L, february.carriedDebtSatang)
            repo.recordPayment(january.id, 10_000, 3, "ชำระหนี้เก่า", 4)
            val adjustedFebruary = repo.billForRoomMonth(room.id, "2026-02")!!
            assertEquals(0L, adjustedFebruary.carriedDebtSatang)
            assertEquals(10_000L, adjustedFebruary.totalDueSatang)
            val summary = repo.financialSummary(february.id)
            assertEquals(10_000L, summary.outstandingSatang)
            assertEquals(PaymentState.UNPAID, summary.bill.paymentState)
            assertTrue(summary.payments.isEmpty())
        } finally { db.close() }
    }

    @Test fun decemberDebtCarriesAcrossYearAndClosedAmountsCannotBeEdited() = runBlocking {
        val (db, repo) = database()
        try {
            val december = bill(repo, "2025-12")
            repo.closeBill(december.id, 2)
            val january = bill(repo, "2026-01")
            assertEquals(10_000L, january.carriedDebtSatang)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.saveReading(room, ReadingDraft(room.id, "2025-12", 0, 5, 0, 0, 0, 0), true, 3) }
            }
        } finally { db.close() }
    }

    @Test fun exportRendersPortraitPngAndBuildsImageShareIntent() = runBlocking {
        val (db, repo) = database()
        try {
            val monthlyBill = bill(repo, "2026-10")
            val summary = repo.financialSummary(monthlyBill.id)
            val bitmap = BillImageExport.render(summary)
            try {
                assertTrue(bitmap.height > bitmap.width)
                assertTrue(bitmap.width >= 900)
                val blank = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFF7F8F5.toInt()) }
                assertFalse(bitmap.sameAs(blank))
                blank.recycle()
            } finally { bitmap.recycle() }
            val chooser = BillImageExport.createShareChooser(context, Uri.parse("content://roomledger/test.png"))
            @Suppress("DEPRECATION") val send = chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT) as? android.content.Intent
            assertEquals(android.content.Intent.ACTION_SEND, send?.action)
            assertEquals("image/png", send?.type)
            @Suppress("DEPRECATION") val stream = send?.getParcelableExtra(android.content.Intent.EXTRA_STREAM) as? Uri
            assertEquals(Uri.parse("content://roomledger/test.png"), stream)
        } finally { db.close() }
    }
}
