package com.pondktpprint.roomledger.presentation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.pondktpprint.roomledger.domain.BillFinancialSummary
import com.pondktpprint.roomledger.domain.formatSatangCurrency
import com.pondktpprint.roomledger.domain.thaiMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Creates a share-ready Thai invoice from the saved bill snapshot and its payment ledger summary. */
object BillImageExport {
    private const val IMAGE_WIDTH = 1080
    private const val RENDER_HEIGHT = 2600
    private const val SIDE = 44f
    private const val CONTENT_WIDTH = IMAGE_WIDTH - SIDE * 2

    private val colors = InvoiceColors()

    fun render(summary: BillFinancialSummary): Bitmap {
        val bill = summary.bill
        val roomNamePaint = textPaint(colors.ink, 50f, bold = true)
        val roomNameLayout = createTextLayout("ห้อง ${bill.roomName}", roomNamePaint, CONTENT_WIDTH.toInt())
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, RENDER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.canvas)

        val x = SIDE
        val right = IMAGE_WIDTH - SIDE
        var y = 34f

        // Brand header and payment state.
        drawBrandIcon(canvas, x + 2f, y + 14f, 112f)
        canvas.drawText("RoomLedger", x + 136f, y + 65f, textPaint(colors.forest, 46f, bold = true))
        canvas.drawText("ใบแจ้งยอดค่าใช้บริการห้องพัก", x + 136f, y + 110f, textPaint(colors.muted, 27f))
        drawStatusBadge(canvas, summary, right - 314f, y + 34f, 314f, 68f)
        y += 164f
        drawRule(canvas, x, right, y)
        y += 38f

        drawTextLayout(canvas, roomNameLayout, x, y)
        y += roomNameLayout.height + 8f
        canvas.drawText(thaiMonth(bill.billingMonth), x, y + 34f, textPaint(colors.muted, 31f))
        y += 62f

        // Outstanding balance is the primary focal point, as in the reference.
        val heroHeight = 300f
        drawHero(canvas, x, y, CONTENT_WIDTH, heroHeight, summary.outstandingSatang)
        y += heroHeight + 22f

        drawSummaryCards(canvas, x, y, CONTENT_WIDTH, summary.totalReceivedSatang, bill.carriedDebtSatang)
        y += 150f

        val rentDescription = "ค่าเช่ารายเดือน"
        y += drawBreakdownCard(
            canvas = canvas,
            x = x,
            y = y,
            width = CONTENT_WIDTH,
            title = "ค่าเช่า",
            subtitle = rentDescription,
            amount = bill.rentSatang,
            icon = InvoiceIcon.HOME,
            iconTint = colors.mint,
        ) + 20f

        y += drawBreakdownCard(
            canvas = canvas,
            x = x,
            y = y,
            width = CONTENT_WIDTH,
            title = "ค่าน้ำ",
            subtitle = "${bill.waterPrevious} → ${bill.waterCurrent}  ·  ${bill.waterUnits} หน่วย × ${formatSatangCurrency(bill.waterRateSatang)} / หน่วย",
            amount = bill.waterChargeSatang,
            icon = InvoiceIcon.WATER,
            iconTint = colors.waterTint,
        ) + 20f

        y += drawBreakdownCard(
            canvas = canvas,
            x = x,
            y = y,
            width = CONTENT_WIDTH,
            title = "ค่าไฟ",
            subtitle = "${bill.electricityPrevious} → ${bill.electricityCurrent}  ·  ${bill.electricityUnits} หน่วย × ${formatSatangCurrency(bill.electricityRateSatang)} / หน่วย",
            amount = bill.electricityChargeSatang,
            icon = InvoiceIcon.POWER,
            iconTint = colors.powerTint,
        ) + 20f

        y += drawBreakdownCard(
            canvas = canvas,
            x = x,
            y = y,
            width = CONTENT_WIDTH,
            title = "สรุปรายการ",
            subtitle = "ค่าใช้จ่ายรอบนี้",
            amount = bill.totalSatang,
            icon = InvoiceIcon.RECEIPT,
            iconTint = colors.mint,
        ) + 34f

        drawTotals(canvas, x, y, CONTENT_WIDTH, summary)
        y += 280f
        drawFooter(canvas, x, right, y)

        val cropHeight = (y + 52f).toInt().coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, cropHeight).also { if (it !== bitmap) bitmap.recycle() }
    }

    /** Status text is derived from the financial summary so a stale payment projection cannot mislabel a receipt. */
    internal fun paymentStatusLabel(summary: BillFinancialSummary): String = when {
        summary.outstandingSatang <= 0L -> "ชำระครบแล้ว"
        summary.totalReceivedSatang > 0L -> "ชำระบางส่วน"
        else -> "ยังไม่ชำระ"
    }

    private fun drawBrandIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.drawCircle(x + size / 2, y + size / 2, size / 2, fill(colors.mint))
        val green = stroke(colors.forest, size * .075f)
        val roof = Path().apply {
            moveTo(x + size * .18f, y + size * .50f)
            lineTo(x + size * .50f, y + size * .20f)
            lineTo(x + size * .82f, y + size * .50f)
        }
        canvas.drawPath(roof, green)
        val walls = Path().apply {
            moveTo(x + size * .27f, y + size * .46f)
            lineTo(x + size * .27f, y + size * .80f)
            lineTo(x + size * .73f, y + size * .80f)
            lineTo(x + size * .73f, y + size * .46f)
        }
        canvas.drawPath(walls, green)
        canvas.drawRoundRect(RectF(x + size * .44f, y + size * .57f, x + size * .57f, y + size * .81f), 4f, 4f, fill(colors.forest))
        // Small utility receipt overlapping the house, matching the RoomLedger logo motif.
        val receipt = RectF(x + size * .63f, y + size * .52f, x + size * .88f, y + size * .82f)
        canvas.drawRoundRect(receipt, 7f, 7f, fill(colors.white))
        canvas.drawRoundRect(receipt, 7f, 7f, stroke(colors.forest, 3f))
        canvas.drawCircle(x + size * .70f, y + size * .61f, size * .025f, fill(colors.primary))
        canvas.drawLine(x + size * .74f, y + size * .61f, x + size * .83f, y + size * .61f, stroke(colors.muted, 2.5f))
        canvas.drawLine(x + size * .69f, y + size * .69f, x + size * .83f, y + size * .69f, stroke(colors.muted, 2.5f))
        canvas.drawLine(x + size * .69f, y + size * .76f, x + size * .80f, y + size * .76f, stroke(colors.muted, 2.5f))
    }

    private fun drawStatusBadge(canvas: Canvas, summary: BillFinancialSummary, x: Float, y: Float, width: Float, height: Float) {
        val label = paymentStatusLabel(summary)
        val (background, foreground) = when (label) {
            "ชำระครบแล้ว" -> colors.mint to colors.forest
            "ชำระบางส่วน" -> colors.warningTint to colors.warningInk
            else -> colors.errorTint to colors.error
        }
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), height / 2, height / 2, fill(background))
        canvas.drawCircle(x + 36f, y + height / 2, 13f, fill(foreground))
        val statusGlyph = when (label) { "ชำระครบแล้ว" -> "✓"; "ชำระบางส่วน" -> "!"; else -> "!" }
        canvas.drawText(statusGlyph, x + 29f, y + 46f, textPaint(colors.white, 19f, bold = true))
        canvas.drawText(label, x + 62f, y + 45f, fittedPaint(label, 28f, 20f, width - 76f, foreground, bold = true))
    }

    private fun drawHero(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, outstanding: Long) {
        val bounds = RectF(x, y, x + width, y + height)
        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x, y, x + width, y + height, colors.forest, colors.primary, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(bounds, 34f, 34f, heroPaint)
        // Soft low-contrast corner shapes keep the panel rich without competing with the balance.
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawCircle(x + 38f, y + height + 8f, 116f, fill(0x1FFFFFFF))
        canvas.drawCircle(x + width - 20f, y + height + 10f, 148f, fill(0x17FFFFFF))
        canvas.restore()

        canvas.drawText("ยอดที่ต้องชำระ", x + width / 2, y + 82f, textPaint(colors.white, 36f, bold = true).apply { textAlign = Paint.Align.CENTER })
        val amount = formatSatangCurrency(outstanding.coerceAtLeast(0L))
        val amountPaint = fittedPaint(amount, 106f, 46f, width - 90f, colors.white, bold = true).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(amount, x + width / 2, y + 194f, amountPaint)
        canvas.drawRoundRect(RectF(x + width / 2 - 44f, y + 230f, x + width / 2 + 44f, y + 239f), 5f, 5f, fill(colors.mintAccent))
    }

    private fun drawSummaryCards(canvas: Canvas, x: Float, y: Float, width: Float, received: Long, carry: Long) {
        val gap = 18f
        val cardWidth = (width - gap) / 2
        drawMiniSummaryCard(canvas, x, y, cardWidth, "รับชำระแล้ว", formatSatangCurrency(received), InvoiceIcon.WALLET)
        drawMiniSummaryCard(canvas, x + cardWidth + gap, y, cardWidth, "ยอดค้างยกมา", formatSatangCurrency(carry), InvoiceIcon.RECEIPT)
    }

    private fun drawMiniSummaryCard(canvas: Canvas, x: Float, y: Float, width: Float, title: String, amount: String, icon: InvoiceIcon) {
        canvas.drawRoundRect(RectF(x, y, x + width, y + 150f), 24f, 24f, fill(colors.mintWash))
        drawIcon(canvas, icon, x + 22f, y + 38f, 64f, colors.mint, colors.forest)
        canvas.drawText(title, x + 102f, y + 53f, fittedPaint(title, 28f, 21f, width - 118f, colors.ink, bold = false))
        canvas.drawText(amount, x + 102f, y + 101f, fittedPaint(amount, 36f, 21f, width - 120f, colors.forest, bold = true))
    }

    private fun drawBreakdownCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        title: String,
        subtitle: String,
        amount: Long,
        icon: InvoiceIcon,
        iconTint: Int,
    ): Float {
        val titleX = x + 108f
        val subtitleLayout = createTextLayout(subtitle, textPaint(colors.muted, 23f), (width - 430f).toInt())
        val height = maxOf(140f, 78f + subtitleLayout.height)
        drawCard(canvas, x, y, width, height)
        drawIcon(canvas, icon, x + 22f, y + 30f, 64f, iconTint, when (icon) { InvoiceIcon.WATER -> colors.water; InvoiceIcon.POWER -> colors.warningInk; else -> colors.forest })
        canvas.drawText(title, titleX, y + 49f, textPaint(colors.ink, 31f, bold = true))
        drawTextLayout(canvas, subtitleLayout, titleX, y + 61f)
        val amountText = formatSatangCurrency(amount)
        val amountPaint = fittedPaint(amountText, 35f, 16f, 290f, colors.forest, bold = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(amountText, x + width - 26f, y + 74f, amountPaint)
        return height
    }

    private fun drawTotals(canvas: Canvas, x: Float, y: Float, width: Float, summary: BillFinancialSummary) {
        val height = 234f
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 28f, 28f, fill(colors.mintWash))
        drawMoneyRow(canvas, x + 30f, x + width - 30f, y + 54f, "ยอดรวมที่เรียกเก็บ", summary.totalBilledSatang, colors.forest, 31f, bold = true)
        drawRule(canvas, x + 30f, x + width - 30f, y + 78f, colors.divider)
        drawMoneyRow(canvas, x + 30f, x + width - 30f, y + 124f, "รับชำระแล้ว", summary.totalReceivedSatang, colors.muted, 28f)
        canvas.drawRoundRect(RectF(x + 12f, y + 150f, x + width - 12f, y + height - 12f), 18f, 18f, fill(colors.mint))
        drawMoneyRow(canvas, x + 30f, x + width - 30f, y + 203f, "คงเหลือที่ต้องชำระ", summary.outstandingSatang.coerceAtLeast(0L), colors.forest, 34f, bold = true)
    }

    private fun drawMoneyRow(canvas: Canvas, left: Float, right: Float, baseline: Float, label: String, amount: Long, color: Int, textSize: Float, bold: Boolean = false) {
        val value = formatSatangCurrency(amount.coerceAtLeast(0L))
        val labelPaint = fittedPaint(label, textSize, 20f, (right - left) * .48f, color, bold)
        val valuePaint = fittedPaint(value, textSize + 2f, 19f, (right - left) * .49f, color, bold = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(label, left, baseline, labelPaint)
        canvas.drawText(value, right, baseline, valuePaint)
    }

    private fun drawFooter(canvas: Canvas, left: Float, right: Float, y: Float) {
        canvas.drawCircle(left + 17f, y + 18f, 14f, fill(colors.muted))
        canvas.drawText("i", left + 13f, y + 27f, textPaint(colors.white, 23f, bold = true))
        canvas.drawText("กรุณาตรวจสอบรายละเอียดก่อนชำระ", left + 43f, y + 27f, textPaint(colors.muted, 23f))
        drawBrandIcon(canvas, right - 166f, y - 5f, 32f)
        canvas.drawText("RoomLedger", right.toFloat(), y + 24f, textPaint(colors.forest, 23f, bold = true).apply { textAlign = Paint.Align.RIGHT })
    }

    private fun drawIcon(canvas: Canvas, icon: InvoiceIcon, x: Float, y: Float, size: Float, background: Int, foreground: Int) {
        canvas.drawCircle(x + size / 2, y + size / 2, size / 2, fill(background))
        when (icon) {
            InvoiceIcon.HOME -> drawHome(canvas, x, y, size, foreground)
            InvoiceIcon.WATER -> {
                val drop = Path().apply {
                    moveTo(x + size * .50f, y + size * .20f)
                    cubicTo(x + size * .35f, y + size * .44f, x + size * .27f, y + size * .55f, x + size * .27f, y + size * .65f)
                    cubicTo(x + size * .27f, y + size * .82f, x + size * .39f, y + size * .91f, x + size * .50f, y + size * .91f)
                    cubicTo(x + size * .64f, y + size * .91f, x + size * .75f, y + size * .80f, x + size * .75f, y + size * .65f)
                    cubicTo(x + size * .75f, y + size * .55f, x + size * .63f, y + size * .40f, x + size * .50f, y + size * .20f)
                }
                canvas.drawPath(drop, fill(foreground))
            }
            InvoiceIcon.POWER -> {
                val bolt = Path().apply {
                    moveTo(x + size * .56f, y + size * .14f)
                    lineTo(x + size * .29f, y + size * .56f)
                    lineTo(x + size * .48f, y + size * .56f)
                    lineTo(x + size * .39f, y + size * .87f)
                    lineTo(x + size * .72f, y + size * .43f)
                    lineTo(x + size * .54f, y + size * .43f)
                    close()
                }
                canvas.drawPath(bolt, fill(foreground))
            }
            InvoiceIcon.RECEIPT, InvoiceIcon.WALLET -> {
                val shape = RectF(x + size * .24f, y + size * .18f, x + size * .76f, y + size * .82f)
                canvas.drawRoundRect(shape, 5f, 5f, stroke(foreground, 4f))
                canvas.drawLine(x + size * .36f, y + size * .39f, x + size * .64f, y + size * .39f, stroke(foreground, 3f))
                canvas.drawLine(x + size * .36f, y + size * .52f, x + size * .64f, y + size * .52f, stroke(foreground, 3f))
                canvas.drawLine(x + size * .36f, y + size * .65f, x + size * .58f, y + size * .65f, stroke(foreground, 3f))
            }
        }
    }

    private fun drawHome(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val roof = Path().apply {
            moveTo(x + size * .18f, y + size * .49f)
            lineTo(x + size * .50f, y + size * .22f)
            lineTo(x + size * .82f, y + size * .49f)
        }
        canvas.drawPath(roof, stroke(color, size * .09f))
        val house = Path().apply {
            moveTo(x + size * .29f, y + size * .45f)
            lineTo(x + size * .29f, y + size * .78f)
            lineTo(x + size * .71f, y + size * .78f)
            lineTo(x + size * .71f, y + size * .45f)
        }
        canvas.drawPath(house, stroke(color, size * .075f))
        canvas.drawRoundRect(RectF(x + size * .43f, y + size * .56f, x + size * .57f, y + size * .79f), 3f, 3f, fill(color))
    }

    private fun createTextLayout(text: String, paint: Paint, width: Int): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, TextPaint(paint), width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()

    private fun drawTextLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawRule(canvas: Canvas, x: Float, right: Float, y: Float, color: Int = colors.divider) {
        canvas.drawLine(x, y, right, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 2f })
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val paint = fill(colors.white).apply { setShadowLayer(8f, 0f, 3f, 0x16003F2D) }
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 24f, 24f, paint)
        paint.clearShadowLayer()
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 24f, 24f, stroke(colors.cardOutline, 1.5f))
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    internal fun fittedPaint(text: String, preferred: Float, minimum: Float, maxWidth: Float, color: Int, bold: Boolean = false): Paint {
        val paint = textPaint(color, preferred, bold)
        while (paint.textSize > minimum && paint.measureText(text) > maxWidth) paint.textSize -= 1f
        return paint
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        return Intent.createChooser(send, "แชร์ใบแจ้งยอด RoomLedger")
    }

    private enum class InvoiceIcon { HOME, WATER, POWER, RECEIPT, WALLET }

    private data class InvoiceColors(
        val canvas: Int = 0xFFFAFAF7.toInt(),
        val white: Int = 0xFFFFFFFF.toInt(),
        val forest: Int = 0xFF07563F.toInt(),
        val primary: Int = 0xFF087653.toInt(),
        val ink: Int = 0xFF17352D.toInt(),
        val muted: Int = 0xFF71817B.toInt(),
        val divider: Int = 0xFFDDE8E2.toInt(),
        val cardOutline: Int = 0xFFE8EFEB.toInt(),
        val mint: Int = 0xFFDDF5E9.toInt(),
        val mintWash: Int = 0xFFF0F8F3.toInt(),
        val mintAccent: Int = 0xFF9CE7C3.toInt(),
        val waterTint: Int = 0xFFE1F5FB.toInt(),
        val water: Int = 0xFF1B9BD1.toInt(),
        val powerTint: Int = 0xFFFFF0D5.toInt(),
        val warningTint: Int = 0xFFFFF0D5.toInt(),
        val warningInk: Int = 0xFFB46A05.toInt(),
        val errorTint: Int = 0xFFFFE9E7.toInt(),
        val error: Int = 0xFFD64545.toInt(),
    )
}
