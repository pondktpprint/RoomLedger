package com.pondktpprint.roomledger.domain

import java.math.BigDecimal

fun formatSatangAsBaht(satang: Long): String =
    BigDecimal.valueOf(satang, 2).setScale(2).toPlainString()
