package com.pondktpprint.roomledger.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.pondktpprint.roomledger.domain.BillFinancialSummary
import com.pondktpprint.roomledger.domain.MonthlyBill
import com.pondktpprint.roomledger.domain.PaymentState
import com.pondktpprint.roomledger.domain.formatSatangCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class BillImageExportTest {
    @Test
    fun exportsReadableThaiPng() {
        val bitmap = BillImageExport.render(sampleSummary())
        try {
            assertEquals(1080, bitmap.width)
            assertTrue(bitmap.height > bitmap.width)
            // The Thai subtitle area must contain rendered ink, not only the off-white canvas.
            assertTrue(countNonCanvasPixels(bitmap, 170, 112, 650, 154) > 0)
            val bytes = ByteArrayOutputStream()
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size()))
            try {
                assertEquals(bitmap.width, decoded.width)
                assertEquals(bitmap.height, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun rendersAllPaymentStatusesAndLongRoomNames() {
        val unpaid = sampleSummary()
        val partial = unpaid.copy(totalReceivedSatang = 200L, outstandingSatang = 100L)
        val paid = unpaid.copy(totalReceivedSatang = 300L, outstandingSatang = 0L)
        assertEquals("ยังไม่ชำระ", BillImageExport.paymentStatusLabel(unpaid))
        assertEquals("ชำระบางส่วน", BillImageExport.paymentStatusLabel(partial))
        assertEquals("ชำระครบแล้ว", BillImageExport.paymentStatusLabel(paid))

        listOf(unpaid, partial, paid).forEach { summary ->
            val bitmap = BillImageExport.render(summary)
            try { assertTrue(bitmap.height > bitmap.width) } finally { bitmap.recycle() }
        }

        val longName = "ห้องพักสำหรับผู้เช่าระยะยาวหมายเลขห้องเอหนึ่งศูนย์หนึ่งชั้นบนอาคารด้านทิศเหนือ"
        val shortImage = BillImageExport.render(unpaid)
        val longImage = BillImageExport.render(unpaid.copy(bill = unpaid.bill.copy(roomName = longName)))
        try { assertTrue("long room name should expand the image rather than clip", longImage.height > shortImage.height) }
        finally { shortImage.recycle(); longImage.recycle() }
    }

    @Test
    fun shareChooserKeepsPngUriAndReadPermission() {
        val uri = Uri.parse("content://com.pondktpprint.roomledger.test/bill.png")
        val chooser = BillImageExport.createShareChooser(InstrumentationRegistry.getInstrumentation().targetContext, uri)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val sendIntent = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals("image/png", sendIntent.type)
        assertEquals(uri, sendIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun saveWritesPngToMediaStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = BillImageExport.save(context, sampleSummary())
        try {
            val bitmap = requireNotNull(context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) })
            try {
                assertEquals(1080, bitmap.width)
                assertTrue(bitmap.height > bitmap.width)
            } finally {
                bitmap.recycle()
            }
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun largeCurrencyValuesStillRenderWithinTheImage() {
        val maximumAmount = formatSatangCurrency(Long.MAX_VALUE)
        val fittedAmount = BillImageExport.fittedPaint(maximumAmount, 35f, 16f, 290f, 0xFF07563F.toInt(), bold = true)
        assertTrue("large right-aligned amounts must fit inside their value column", fittedAmount.measureText(maximumAmount) <= 290f)

        val summary = sampleSummary().copy(
            totalBilledSatang = Long.MAX_VALUE,
            totalReceivedSatang = Long.MAX_VALUE / 2,
            outstandingSatang = Long.MAX_VALUE / 2,
            bill = sampleSummary().bill.copy(
                rentSatang = Long.MAX_VALUE,
                totalSatang = Long.MAX_VALUE,
                totalDueSatang = Long.MAX_VALUE,
                waterRateSatang = Long.MAX_VALUE,
                waterChargeSatang = Long.MAX_VALUE,
                electricityRateSatang = Long.MAX_VALUE,
                electricityChargeSatang = Long.MAX_VALUE,
                waterUnits = Long.MAX_VALUE,
                electricityUnits = Long.MAX_VALUE,
            ),
        )
        val bitmap = BillImageExport.render(summary)
        try { assertTrue(bitmap.height in 1_800..2_600) } finally { bitmap.recycle() }
    }

    private fun countNonCanvasPixels(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        val background = bitmap.getPixel(0, 0)
        var count = 0
        for (y in top until bottom) for (x in left until right) if (bitmap.getPixel(x, y) != background) count++
        return count
    }

    private fun sampleSummary(): BillFinancialSummary {
        val bill = MonthlyBill(
            id = "bill-test",
            roomId = "room-test",
            roomName = "A101",
            billingMonth = "2026-11",
            waterPrevious = 105,
            waterCurrent = 110,
            waterAdjustment = 0,
            waterUnits = 5,
            waterRateSatang = 1_600,
            waterChargeSatang = 8_000,
            electricityPrevious = 1_649,
            electricityCurrent = 1_685,
            electricityAdjustment = 0,
            electricityUnits = 36,
            electricityRateSatang = 600,
            electricityChargeSatang = 21_600,
            rentSatang = 130_000,
            totalSatang = 159_600,
            paymentState = PaymentState.UNPAID,
            createdAtMillis = 0,
            updatedAtMillis = 0,
            carriedDebtSatang = 20_600,
            totalDueSatang = 180_200,
        )
        return BillFinancialSummary(bill, 180_200, 0, 180_200, emptyList(), false, null)
    }
}
