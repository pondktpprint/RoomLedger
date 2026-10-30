package com.pondktpprint.roomledger.domain

data class RoomRecord(
    val id: String,
    val name: String,
    val monthlyRentSatang: Long,
    val waterPricePerUnitSatang: Long,
    val electricityPricePerUnitSatang: Long,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val groupId: String? = null,
)
