package com.pondktpprint.roomledger.presentation

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pondktpprint.roomledger.domain.BillFinancialSummary
import com.pondktpprint.roomledger.domain.MonthlyBill
import com.pondktpprint.roomledger.domain.PaymentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BillImageExportTest {
    @Test
    fun renderCreatesReadablePortraitPng() {
        val bitmap = BillImageExport.render(sampleSummary())
        try {
            assertEquals(1080, bitmap.width)
            assertTrue(bitmap.height > bitmap.width)
            val output = ByteArrayOutputStream()
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size()))
            assertEquals(bitmap.width, decoded.width)
            assertEquals(bitmap.height, decoded.height)
            decoded.recycle()
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun shareIntentIncludesPngUriAndReadPermission() {
        val uri = Uri.parse("content://com.pondktpprint.roomledger.test/bill.png")
        val chooser = BillImageExport.createShareChooser(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, uri)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val sendIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertTrue(sendIntent != null)
        assertEquals("image/png", sendIntent?.type)
        assertEquals(uri, sendIntent?.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue((sendIntent?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun sampleSummary(): BillFinancialSummary {
        val bill = MonthlyBill(
            id = "bill-test",
            roomId = "room-test",
            roomName = "ห้อง A101 ห้องพักชั้นสอง",
            billingMonth = "2026-10",
            waterPrevious = 120,
            waterCurrent = 135,
            waterAdjustment = 0,
            waterUnits = 15,
            waterRateSatang = 1_000,
            waterChargeSatang = 15_000,
            electricityPrevious = 450,
            electricityCurrent = 480,
            electricityAdjustment = 0,
            electricityUnits = 30,
            electricityRateSatang = 600,
            electricityChargeSatang = 18_000,
            rentSatang = 350_000,
            totalSatang = 383_000,
            paymentState = PaymentState.UNPAID,
            createdAtMillis = 0,
            updatedAtMillis = 0,
            carriedDebtSatang = 100_000,
            totalDueSatang = 483_000,
        )
        return BillFinancialSummary(bill, 483_000, 20_000, 463_000, emptyList(), false, null)
    }
}
