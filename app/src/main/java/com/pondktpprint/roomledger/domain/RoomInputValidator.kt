package com.pondktpprint.roomledger.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class RoomInput(
    val name: String,
    val monthlyRentSatang: Long,
    val waterPricePerUnitSatang: Long,
    val electricityPricePerUnitSatang: Long,
)

sealed interface RoomInputResult {
    data class Valid(val input: RoomInput) : RoomInputResult
    data class Invalid(val message: String) : RoomInputResult
}

/** Parses user-entered baht as decimal text, avoiding floating-point rounding in stored money. */
object RoomInputValidator {
    private const val MAX_NAME_LENGTH = 60
    private val amountPattern = Regex("\\d+(\\.\\d{1,2})?")

    fun validate(name: String, rent: String, water: String, electricity: String): RoomInputResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return RoomInputResult.Invalid("กรุณากรอกชื่อห้อง")
        if (cleanName.length > MAX_NAME_LENGTH) {
            return RoomInputResult.Invalid("ชื่อห้องต้องไม่เกิน $MAX_NAME_LENGTH ตัวอักษร")
        }

        val rentSatang = parseSatang(rent)
            ?: return RoomInputResult.Invalid("ค่าเช่าต้องเป็นตัวเลขไม่เกิน 2 ตำแหน่งทศนิยม")
        if (rentSatang <= 0L) return RoomInputResult.Invalid("ค่าเช่าต้องมากกว่า 0 บาท")

        val waterSatang = parseSatang(water)
            ?: return RoomInputResult.Invalid("ค่าน้ำต้องเป็นตัวเลขไม่เกิน 2 ตำแหน่งทศนิยม")
        val electricitySatang = parseSatang(electricity)
            ?: return RoomInputResult.Invalid("ค่าไฟต้องเป็นตัวเลขไม่เกิน 2 ตำแหน่งทศนิยม")

        return RoomInputResult.Valid(
            RoomInput(
                name = cleanName,
                monthlyRentSatang = rentSatang,
                waterPricePerUnitSatang = waterSatang,
                electricityPricePerUnitSatang = electricitySatang,
            ),
        )
    }

    private fun parseSatang(value: String): Long? {
        val normalized = value.trim()
        if (!amountPattern.matches(normalized)) return null
        return runCatching {
            BigDecimal(normalized)
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()
    }
}
