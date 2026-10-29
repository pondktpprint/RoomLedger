package com.pondktpprint.roomledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun formatsSatangWithTwoDecimalPlaces() {
        assertEquals("1,200.00", formatSatangAsBaht(120_000L))
        assertEquals("0.05", formatSatangAsBaht(5L))
    }
}
