package com.pondktpprint.roomledger.presentation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

/** Draws the bill from immutable bill snapshots and the financial ledger summary. */
object BillImageExport {
    private const val WIDTH = 1080
    private const val HEIGHT = 3200
    private const val LEFT = 82f
    private const val RIGHT = 998f

    fun render(summary: BillFinancialSummary): Bitmap {
        val bill = summary.bill
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val forest = 0xFF07563F.toInt()
        val primary = 0xFF087653.toInt()
        val ink = 0xFF202A27.toInt()
        val muted = 0xFF737D79.toInt()
        val mint = 0xFFD9F5E7.toInt()
        val paper = 0xFFFAFAF7.toInt()
        val lineColor = 0xFFE2E9E4.toInt()
        canvas.drawColor(paper)
        val card = RectF(40f, 38f, WIDTH - 40f, HEIGHT - 38f)
        canvas.drawRoundRect(card, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })

        val heading = paint(ink, 38f, true)
        val sectionPaint = paint(primary, 31f, true)
        val body = paint(ink, 29f, false)
        val mutedPaint = paint(muted, 27f, false)
        val boldBody = paint(ink, 30f, true)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor; strokeWidth = 2f }
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = forest }

        fun wrapped(text: String, x: Float, baseline: Float, maxWidth: Float, textPaint: Paint): Float {
            val words = text.split(Regex("\\s+")).filter(String::isNotEmpty)
            var current = ""
            var lineY = baseline
            fun finishLine() {
                if (current.isNotEmpty()) {
                    canvas.drawText(current, x, lineY, textPaint)
                    lineY += textPaint.textSize * 1.28f
                    current = ""
                }
            }
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && textPaint.measureText(candidate) > maxWidth) finishLine()
                if (textPaint.measureText(word) > maxWidth) {
                    val iterator = java.text.BreakIterator.getCharacterInstance(java.util.Locale("th", "TH"))
                    iterator.setText(word)
                    var start = iterator.first()
                    var end = iterator.next()
                    while (end != java.text.BreakIterator.DONE) {
                        val segment = word.substring(start, end)
                        if (current.isNotEmpty() && textPaint.measureText(current + segment) > maxWidth) finishLine()
                        current += segment
                        start = end
                        end = iterator.next()
                    }
                } else current = if (current.isEmpty()) word else "$current $word"
            }
            val lineCountHeight = if (lineY == baseline && current.isEmpty()) 0f else {
                if (current.isNotEmpty()) canvas.drawText(current, x, lineY, textPaint)
                lineY - baseline + textPaint.textSize * 1.28f
            }
            return lineCountHeight
        }

        var y = 72f
        // Branded receipt header.
        canvas.drawRoundRect(RectF(40f, 38f, WIDTH - 40f, 420f), 36f, 36f, green)
        drawBrandMark(canvas, 88f, 74f, 102f, 0xFFFFFFFF.toInt())
        canvas.drawText("RoomLedger", 218f, 133f, paint(0xFFFFFFFF.toInt(), 49f, true))
        canvas.drawText("ใบแจ้งยอดค่าใช้บริการห้องพัก", 218f, 184f, paint(0xFFE5F4EB.toInt(), 29f, false))
        wrapped("ห้อง ${bill.roomName}", LEFT, 255f, RIGHT - LEFT, paint(0xFFFFFFFF.toInt(), 35f, true))
        y = 474f
        canvas.drawText("บิลประจำเดือน ${thaiMonth(bill.billingMonth)}", LEFT, y, heading)
        y += 42f
        canvas.drawText("สรุปรายการค่าใช้บริการ", LEFT, y, mutedPaint)
        y += 38f
        canvas.drawLine(LEFT, y, RIGHT, y, rule)
        y += 55f

        fun section(title: String) {
            canvas.drawRoundRect(RectF(LEFT - 12f, y - 32f, RIGHT + 12f, y + 18f), 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mint })
            canvas.drawText(title, LEFT, y + 2f, sectionPaint)
            y += 68f
        }

        fun row(label: String, value: String, bold: Boolean = false) {
            val labelPaint = if (bold) boldBody else body
            val valuePaint = if (bold) boldBody else body
            val valueWidth = valuePaint.measureText(value)
            val availableLabel = RIGHT - LEFT - valueWidth - 34f
            val used = wrapped(label, LEFT, y, availableLabel, labelPaint)
            val valueBaseline = y + (used - valuePaint.textSize * 1.28f).coerceAtLeast(0f)
            canvas.drawText(value, RIGHT - valueWidth, valueBaseline, valuePaint)
            y += maxOf(49f, used + 5f)
        }

        section("ค่าเช่า")
        row("ค่าเช่ารายเดือน", formatSatangCurrency(bill.rentSatang))
        y += 8f

        section("ค่าน้ำ")
        row("เลขมิเตอร์ครั้งก่อน", bill.waterPrevious.toString())
        row("เลขมิเตอร์ปัจจุบัน", bill.waterCurrent.toString())
        row("ปริมาณการใช้", "${bill.waterUnits} หน่วย")
        row("อัตราต่อหน่วย", formatSatangCurrency(bill.waterRateSatang))
        row("รวมค่าน้ำ", formatSatangCurrency(bill.waterChargeSatang), true)
        y += 8f

        section("ค่าไฟ")
        row("เลขมิเตอร์ครั้งก่อน", bill.electricityPrevious.toString())
        row("เลขมิเตอร์ปัจจุบัน", bill.electricityCurrent.toString())
        row("ปริมาณการใช้", "${bill.electricityUnits} หน่วย")
        row("อัตราต่อหน่วย", formatSatangCurrency(bill.electricityRateSatang))
        row("รวมค่าไฟ", formatSatangCurrency(bill.electricityChargeSatang), true)
        y += 8f

        section("สรุปยอด")
        if (bill.carriedDebtSatang > 0) row("ยอดค้างยกมาจากรอบก่อน", formatSatangCurrency(bill.carriedDebtSatang))
        row("ค่าใช้จ่ายรอบนี้", formatSatangCurrency(bill.totalSatang))
        row("ยอดรวมที่เรียกเก็บ", formatSatangCurrency(summary.totalBilledSatang), true)
        y += 10f
        canvas.drawLine(LEFT, y, RIGHT, y, rule)
        y += 52f
        row("รับชำระแล้ว", formatSatangCurrency(summary.totalReceivedSatang))
        row("คงเหลือที่ต้องชำระ", formatSatangCurrency(summary.outstandingSatang), true)
        y += 34f
        canvas.drawRoundRect(RectF(LEFT - 14f, y - 38f, RIGHT + 14f, y + 24f), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mint })
        canvas.drawText(if (summary.isClosed) "สถานะบิล: ปิดบิลแล้ว" else "สถานะบิล: ยังไม่ปิด", LEFT, y + 3f, sectionPaint)
        y += 72f
        canvas.drawText("ขอบคุณที่ใช้บริการ RoomLedger", LEFT, y, mutedPaint)

        val cropHeight = (y + 55f).toInt().coerceAtMost(HEIGHT)
        return Bitmap.createBitmap(bitmap, 0, 0, WIDTH, cropHeight).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun paint(color: Int, textSize: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = textSize
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun drawBrandMark(canvas: Canvas, left: Float, top: Float, size: Float, color: Int) {
        val scale = size / 100f
        val roof = Path().apply {
            moveTo(left + 12f * scale, top + 48f * scale)
            lineTo(left + 50f * scale, top + 16f * scale)
            lineTo(left + 88f * scale, top + 48f * scale)
        }
        canvas.drawPath(roof, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 9f * scale; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
        val house = Path().apply {
            moveTo(left + 22f * scale, top + 45f * scale)
            lineTo(left + 22f * scale, top + 86f * scale)
            lineTo(left + 78f * scale, top + 86f * scale)
            lineTo(left + 78f * scale, top + 45f * scale)
        }
        canvas.drawPath(house, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 7f * scale; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
        canvas.drawRoundRect(RectF(left + 40f * scale, top + 57f * scale, left + 58f * scale, top + 87f * scale), 2f * scale, 2f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    suspend fun save(context: Context, summary: BillFinancialSummary): Uri = withContext(Dispatchers.IO) {
        val bitmap = render(summary)
        try {
            val name = "RoomLedger-${summary.bill.roomId}-${summary.bill.billingMonth}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RoomLedger")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(collection, values) ?: throw IOException("บันทึกรูปไม่สำเร็จ")
            try {
                val stream = context.contentResolver.openOutputStream(uri) ?: throw IOException("เปิดไฟล์รูปไม่สำเร็จ")
                stream.use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("สร้างไฟล์ PNG ไม่สำเร็จ") }
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                uri
            } catch (failure: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw failure
            }
        } finally { bitmap.recycle() }
    }

    fun share(context: Context, uri: Uri) {
        context.startActivity(createShareChooser(context, uri))
    }

    fun createShareChooser(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "RoomLedger bill", uri)
        }
        return Intent.createChooser(send, "แชร์รูปบิล")
    }
}
