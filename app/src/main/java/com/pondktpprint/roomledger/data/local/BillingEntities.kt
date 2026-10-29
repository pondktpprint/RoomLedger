package com.pondktpprint.roomledger.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "meter_readings", foreignKeys = [ForeignKey(entity = RoomEntity::class, parentColumns = ["id"], childColumns = ["roomId"])], indices = [Index(value = ["roomId", "billingMonth"], unique = true), Index("roomId")])
data class MeterReadingEntity(
    @PrimaryKey val id: String, val roomId: String, val billingMonth: String,
    val waterPrevious: Long, val waterCurrent: Long, val waterAdjustment: Long, val waterUnits: Long,
    val electricityPrevious: Long, val electricityCurrent: Long, val electricityAdjustment: Long, val electricityUnits: Long,
    val createdAtMillis: Long, val updatedAtMillis: Long,
)

@Entity(tableName = "bills", foreignKeys = [ForeignKey(entity = RoomEntity::class, parentColumns = ["id"], childColumns = ["roomId"]), ForeignKey(entity = MeterReadingEntity::class, parentColumns = ["id"], childColumns = ["readingId"])], indices = [Index(value = ["roomId", "billingMonth"], unique = true), Index(value = ["readingId"], unique = true), Index("roomId")])
data class BillEntity(
    @PrimaryKey val id: String, val readingId: String, val roomId: String, val roomName: String, val billingMonth: String,
    val waterPrevious: Long, val waterCurrent: Long, val waterAdjustment: Long, val waterUnits: Long, val waterRateSatang: Long, val waterChargeSatang: Long,
    val electricityPrevious: Long, val electricityCurrent: Long, val electricityAdjustment: Long, val electricityUnits: Long, val electricityRateSatang: Long, val electricityChargeSatang: Long,
    val rentSatang: Long, val totalSatang: Long, val paymentState: String,
    val createdAtMillis: Long, val updatedAtMillis: Long,
)
