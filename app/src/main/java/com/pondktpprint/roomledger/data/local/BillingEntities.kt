package com.pondktpprint.roomledger.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
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
    val rentSatang: Long, val totalSatang: Long,
    @ColumnInfo(defaultValue = "0") val carriedDebtSatang: Long,
    @ColumnInfo(defaultValue = "0") val totalDueSatang: Long,
    @ColumnInfo(defaultValue = "NULL") val closedAtMillis: Long?,
    val paymentState: String,
    val createdAtMillis: Long, val updatedAtMillis: Long,
)

@Entity(tableName = "payments", foreignKeys = [ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"]), ForeignKey(entity = PaymentEntity::class, parentColumns = ["id"], childColumns = ["reversesPaymentId"])], indices = [Index("billId"), Index(value = ["reversesPaymentId"], unique = true), Index("paymentDateMillis")])
data class PaymentEntity(
    @PrimaryKey val id: String,
    val billId: String,
    val amountSatang: Long,
    val paymentDateMillis: Long,
    val note: String?,
    val entryType: String,
    val reversesPaymentId: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(tableName = "payment_allocations", foreignKeys = [ForeignKey(entity = PaymentEntity::class, parentColumns = ["id"], childColumns = ["paymentId"]), ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["sourceBillId"])], indices = [Index(value = ["paymentId", "sourceBillId"], unique = true), Index("sourceBillId")])
data class PaymentAllocationEntity(
    @PrimaryKey val id: String,
    val paymentId: String,
    val sourceBillId: String,
    val amountSatang: Long,
    val createdAtMillis: Long,
)

@Entity(tableName = "debt_carry_forwards", foreignKeys = [ForeignKey(entity = RoomEntity::class, parentColumns = ["id"], childColumns = ["roomId"]), ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["sourceBillId"]), ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["targetBillId"])], indices = [Index(value = ["sourceBillId", "targetMonth"], unique = true), Index("targetBillId"), Index(value = ["roomId", "targetMonth"])])
data class DebtCarryForwardEntity(
    @PrimaryKey val id: String,
    val sourceBillId: String,
    val roomId: String,
    val targetMonth: String,
    val targetBillId: String?,
    val amountSatang: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
