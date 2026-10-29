package com.pondktpprint.roomledger.presentation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pondktpprint.roomledger.domain.BillFinancialSummary
import com.pondktpprint.roomledger.domain.formatSatangCurrency
import com.pondktpprint.roomledger.domain.thaiMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object BillImageExport {
    fun render(summary: BillFinancialSummary): Bitmap {
        val bill = summary.bill
        val bitmap = Bitmap.createBitmap(1080, 2100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF7F8F5.toInt())
        val primary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF216B52.toInt(); typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF17221C.toInt(); typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334139.toInt(); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF68756E.toInt(); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD8E2DB.toInt(); strokeWidth = 2f }
        val x = 72f; val right = 1008f
        var y = 112f
        canvas.drawText("RoomLedger", x, y, primary.apply { textSize = 46f })
        y += 70f; canvas.drawText(bill.roomName, x, y, heading.apply { textSize = 42f })
        y += 52f; canvas.drawText("บิลประจำเดือน ${thaiMonth(bill.billingMonth)}", x, y, muted.apply { textSize = 30f })
        y += 38f; canvas.drawLine(x, y, right, y, rule); y += 56f
        fun row(label: String, value: String, bold: Boolean = false) {
            val p = if (bold) heading.apply { textSize = 31f } else body.apply { textSize = 29f }
            canvas.drawText(label, x, y, p)
            val valueWidth = p.measureText(value)
            canvas.drawText(value, right - valueWidth, y, p)
            y += 48f
        }
        fun section(title: String) { y += 12f; canvas.drawText(title, x, y, primary.apply { textSize = 31f }); y += 46f }
        section("ค่าเช่า"); row("ค่าเช่ารายเดือน", formatSatangCurrency(bill.rentSatang))
        section("ค่าน้ำ"); row("เลขครั้งก่อน", bill.waterPrevious.toString()); row("เลขปัจจุบัน", bill.waterCurrent.toString()); row("ปริมาณการใช้", "${bill.waterUnits} หน่วย"); row("อัตราต่อหน่วย", formatSatangCurrency(bill.waterRateSatang)); row("รวมค่าน้ำ", formatSatangCurrency(bill.waterChargeSatang), true)
        section("ค่าไฟ"); row("เลขครั้งก่อน", bill.electricityPrevious.toString()); row("เลขปัจจุบัน", bill.electricityCurrent.toString()); row("ปริมาณการใช้", "${bill.electricityUnits} หน่วย"); row("อัตราต่อหน่วย", formatSatangCurrency(bill.electricityRateSatang)); row("รวมค่าไฟ", formatSatangCurrency(bill.electricityChargeSatang), true)
        section("สรุปยอด"); if (bill.carriedDebtSatang > 0) row("ยอดค้างจากรอบก่อน", formatSatangCurrency(bill.carriedDebtSatang))
        row("ค่าใช้จ่ายรอบนี้", formatSatangCurrency(bill.totalSatang)); row("ยอดรวมที่เรียกเก็บ", formatSatangCurrency(summary.totalBilledSatang), true)
        y += 12f; canvas.drawLine(x, y, right, y, rule); y += 48f
        row("รับชำระแล้ว", formatSatangCurrency(summary.totalReceivedSatang)); row("ยอดคงเหลือ", formatSatangCurrency(summary.outstandingSatang), true)
        y += 28f; canvas.drawText(if (summary.isClosed) "สถานะบิล: ปิดบิลแล้ว" else "สถานะบิล: ยังไม่ปิด", x, y, muted.apply { textSize = 26f })
        val cropHeight = (y + 72f).toInt().coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, cropHeight).also { if (it !== bitmap) bitmap.recycle() }
    }

    suspend fun save(context: Context, summary: BillFinancialSummary): Uri = withContext(Dispatchers.IO) {
        val bitmap = render(summary)
        try {
            val name = "RoomLedger-${summary.bill.roomId}-${summary.bill.billingMonth}.png"
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RoomLedger"); put(MediaStore.Images.Media.IS_PENDING, 1) } }
            val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(collection, values) ?: throw IOException("บันทึกรูปไม่สำเร็จ")
            try {
                val stream = context.contentResolver.openOutputStream(uri) ?: throw IOException("เปิดไฟล์รูปไม่สำเร็จ")
                stream.use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("สร้างไฟล์ PNG ไม่สำเร็จ") }
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                uri
            } catch (failure: Throwable) { context.contentResolver.delete(uri, null, null); throw failure }
        } finally { bitmap.recycle() }
    }

    fun share(context: Context, uri: Uri) {
        context.startActivity(createShareChooser(context, uri))
    }

    fun createShareChooser(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); clipData = android.content.ClipData.newUri(context.contentResolver, "RoomLedger bill", uri) }
        return Intent.createChooser(send, "แชร์รูปบิล")
    }
}
