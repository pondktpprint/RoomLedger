package com.pondktpprint.roomledger.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

fun formatSatangAsBaht(satang: Long): String =
    NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        .format(BigDecimal.valueOf(satang, 2))
