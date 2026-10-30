package com.pondktpprint.roomledger.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pondktpprint.roomledger.domain.RoomRecord

@Entity(
    tableName = "rooms",
    foreignKeys = [ForeignKey(entity = RoomGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("groupId")],
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val monthlyRentSatang: Long,
    val waterPricePerUnitSatang: Long,
    val electricityPricePerUnitSatang: Long,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "NULL") val groupId: String? = null,
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
    groupId = groupId,
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
    groupId = groupId,
)
