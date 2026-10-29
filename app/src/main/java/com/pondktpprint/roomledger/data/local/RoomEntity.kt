package com.pondktpprint.roomledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pondktpprint.roomledger.domain.RoomRecord

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val monthlyRentSatang: Long,
    val waterPricePerUnitSatang: Long,
    val electricityPricePerUnitSatang: Long,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

fun RoomEntity.toDomain() = RoomRecord(
    id = id,
    name = name,
    monthlyRentSatang = monthlyRentSatang,
    waterPricePerUnitSatang = waterPricePerUnitSatang,
    electricityPricePerUnitSatang = electricityPricePerUnitSatang,
    isActive = isActive,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun RoomRecord.toEntity() = RoomEntity(
    id = id,
    name = name,
    monthlyRentSatang = monthlyRentSatang,
    waterPricePerUnitSatang = waterPricePerUnitSatang,
    electricityPricePerUnitSatang = electricityPricePerUnitSatang,
    isActive = isActive,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
