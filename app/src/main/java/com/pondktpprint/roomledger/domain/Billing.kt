package com.pondktpprint.roomledger.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

enum class PaymentState { UNPAID, PAID }
data class MeterInput(val previous: Long?, val current: Long?, val adjustmentUnits: Long = 0)
data class BillCalculation(val waterUnits: Long, val electricityUnits: Long, val waterSatang: Long, val electricitySatang: Long, val rentSatang: Long, val totalSatang: Long)

object BillingCalculator {
    fun calculate(room: RoomRecord, water: MeterInput, electricity: MeterInput): BillCalculation {
        fun usage(meter: MeterInput): Long {
            require(meter.previous != null && meter.current != null) { "กรุณากรอกเลขมิเตอร์ให้ครบ" }
            require(meter.previous >= 0 && meter.current >= 0 && meter.adjustmentUnits >= 0) { "เลขมิเตอร์และค่าปรับต้องไม่ติดลบ" }
            val raw = try { Math.subtractExact(meter.current, meter.previous) } catch (_: ArithmeticException) { throw IllegalArgumentException("เลขมิเตอร์ไม่ถูกต้อง") }
            require(raw >= 0 || meter.adjustmentUnits > 0) { "เลขปัจจุบันน้อยกว่าเลขครั้งก่อน กรุณาระบุการเปลี่ยน/หมุนมิเตอร์" }
            val units = try { if (raw < 0) meter.adjustmentUnits else Math.addExact(raw, meter.adjustmentUnits) } catch (_: ArithmeticException) { throw IllegalArgumentException("ปริมาณการใช้เกินขอบเขต") }
            require(units >= 0) { "ปริมาณการใช้ติดลบ" }
            return units
        }
        val waterUnits = usage(water); val electricityUnits = usage(electricity)
        return try {
            val w = Math.multiplyExact(waterUnits, room.waterPricePerUnitSatang)
            val e = Math.multiplyExact(electricityUnits, room.electricityPricePerUnitSatang)
            BillCalculation(waterUnits, electricityUnits, w, e, room.monthlyRentSatang, Math.addExact(Math.addExact(room.monthlyRentSatang, w), e))
        } catch (_: ArithmeticException) { throw IllegalArgumentException("ยอดเงินเกินขอบเขตที่รองรับ") }
    }
}

data class ReadingDraft(val roomId: String, val billingMonth: String, val waterPrevious: Long, val waterCurrent: Long, val waterAdjustment: Long, val electricityPrevious: Long, val electricityCurrent: Long, val electricityAdjustment: Long)
data class MonthlyBill(val id: String, val roomId: String, val roomName: String, val billingMonth: String, val waterPrevious: Long, val waterCurrent: Long, val waterAdjustment: Long, val waterUnits: Long, val waterRateSatang: Long, val waterChargeSatang: Long, val electricityPrevious: Long, val electricityCurrent: Long, val electricityAdjustment: Long, val electricityUnits: Long, val electricityRateSatang: Long, val electricityChargeSatang: Long, val rentSatang: Long, val totalSatang: Long, val paymentState: PaymentState, val createdAtMillis: Long, val updatedAtMillis: Long)

fun formatSatangCurrency(satang: Long): String = "฿" + NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(BigDecimal.valueOf(satang, 2))
fun parseBillingMonth(month: String): YearMonth? = if (!month.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) null else runCatching { YearMonth.parse(month) }.getOrNull()
fun formatBillingMonth(month: YearMonth): String = "${month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("th", "TH"))} ${month.year + 543}"
fun thaiMonth(month: String): String = parseBillingMonth(month)?.let(::formatBillingMonth) ?: month
