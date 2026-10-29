package com.pondktpprint.roomledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BillingCalculatorTest {
    private val room = RoomRecord("r", "A-01", 500_000, 2_000, 500, true, 1, 1)

    @Test fun calculatesWaterElectricityAndTotalWithIntegerSatang() {
        val result = BillingCalculator.calculate(room, MeterInput(10, 15), MeterInput(20, 24))
        assertEquals(5L, result.waterUnits); assertEquals(4L, result.electricityUnits)
        assertEquals(10_000L, result.waterSatang); assertEquals(2_000L, result.electricitySatang)
        assertEquals(512_000L, result.totalSatang)
    }

    @Test fun requiresManualPreviousReadingsAndExplicitAdjustmentForRollover() {
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(room, MeterInput(null, 2), MeterInput(1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(room, MeterInput(9, 2), MeterInput(1, 2)) }
        assertEquals(3L, BillingCalculator.calculate(room, MeterInput(9, 2, 3), MeterInput(1, 2)).waterUnits)
    }

    @Test fun rejectsNegativeValuesAndArithmeticOverflow() {
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(room, MeterInput(-1, 2), MeterInput(1, 2)) }
        val expensive = room.copy(waterPricePerUnitSatang = Long.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { BillingCalculator.calculate(expensive, MeterInput(0, 2), MeterInput(0, 0)) }
    }

    @Test fun formatsCurrencyWithSeparatorsAndTwoDecimals() {
        assertEquals("฿12,345.67", formatSatangCurrency(1_234_567))
    }
}
