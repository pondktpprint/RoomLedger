package com.pondktpprint.roomledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomInputValidatorTest {
    @Test
    fun parsesBahtIntoExactSatang() {
        val result = RoomInputValidator.validate("  A-101 ", "12500.75", "18.5", "7")
        assertTrue(result is RoomInputResult.Valid)
        val input = (result as RoomInputResult.Valid).input
        assertEquals("A-101", input.name)
        assertEquals(1_250_075L, input.monthlyRentSatang)
        assertEquals(1_850L, input.waterPricePerUnitSatang)
        assertEquals(700L, input.electricityPricePerUnitSatang)
    }

    @Test
    fun rejectsBlankNameAndNonPositiveRent() {
        assertTrue(RoomInputValidator.validate("  ", "100", "0", "0") is RoomInputResult.Invalid)
        assertTrue(RoomInputValidator.validate("A-101", "0", "0", "0") is RoomInputResult.Invalid)
    }

    @Test
    fun rejectsMoreThanTwoDecimalPlacesAndOverflow() {
        assertTrue(RoomInputValidator.validate("A-101", "10.001", "0", "0") is RoomInputResult.Invalid)
        assertTrue(RoomInputValidator.validate("A-101", "999999999999999999999999", "0", "0") is RoomInputResult.Invalid)
    }

    @Test
    fun allowsZeroUtilityRates() {
        assertTrue(RoomInputValidator.validate("A-101", "1", "0", "0.00") is RoomInputResult.Valid)
    }

    @Test
    fun acceptsProperlyGroupedMoneyInput() {
        val result = RoomInputValidator.validate("A-101", "12,500.75", "1,200", "7")
        assertTrue(result is RoomInputResult.Valid)
        assertEquals(1_250_075L, (result as RoomInputResult.Valid).input.monthlyRentSatang)
        assertTrue(RoomInputValidator.validate("A-101", "12,,500", "0", "0") is RoomInputResult.Invalid)
    }
}
