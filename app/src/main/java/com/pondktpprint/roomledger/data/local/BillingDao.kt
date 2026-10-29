package com.pondktpprint.roomledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BillingDao {
    @Query("SELECT * FROM bills WHERE billingMonth = :month ORDER BY roomName COLLATE NOCASE") suspend fun billsForMonth(month: String): List<BillEntity>
    @Query("SELECT * FROM bills WHERE roomId = :roomId ORDER BY billingMonth DESC") suspend fun history(roomId: String): List<BillEntity>
    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1") suspend fun findBill(id: String): BillEntity?
    @Query("SELECT * FROM bills WHERE readingId = :readingId LIMIT 1") suspend fun findBillForReading(readingId: String): BillEntity?
    @Query("SELECT * FROM meter_readings WHERE roomId = :roomId AND billingMonth < :month ORDER BY billingMonth DESC LIMIT 1") suspend fun priorReading(roomId: String, month: String): MeterReadingEntity?
    @Query("SELECT COUNT(*) FROM meter_readings WHERE roomId = :roomId AND billingMonth > :month") suspend fun laterReadingCount(roomId: String, month: String): Int
    @Query("SELECT * FROM meter_readings WHERE roomId = :roomId AND billingMonth = :month LIMIT 1") suspend fun reading(roomId: String, month: String): MeterReadingEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReading(reading: MeterReadingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBill(bill: BillEntity)
    @Query("UPDATE meter_readings SET waterPrevious=:wp, waterCurrent=:wc, waterAdjustment=:wa, waterUnits=:wu, electricityPrevious=:ep, electricityCurrent=:ec, electricityAdjustment=:ea, electricityUnits=:eu, updatedAtMillis=:updated WHERE id=:id")
    suspend fun updateReading(id: String, wp: Long, wc: Long, wa: Long, wu: Long, ep: Long, ec: Long, ea: Long, eu: Long, updated: Long)
    @Query("UPDATE bills SET waterPrevious=:wp, waterCurrent=:wc, waterAdjustment=:wa, waterUnits=:wu, waterChargeSatang=:wcharge, electricityPrevious=:ep, electricityCurrent=:ec, electricityAdjustment=:ea, electricityUnits=:eu, electricityChargeSatang=:echarge, rentSatang=:rent, totalSatang=:total, paymentState=:payment, updatedAtMillis=:updated WHERE id=:id")
    suspend fun updateBill(id: String, wp: Long, wc: Long, wa: Long, wu: Long, wcharge: Long, ep: Long, ec: Long, ea: Long, eu: Long, echarge: Long, rent: Long, total: Long, payment: String, updated: Long)
    @Query("UPDATE bills SET paymentState=:state, updatedAtMillis=:updated WHERE id=:id AND totalSatang=:confirmedTotal") suspend fun reconcilePayment(id: String, state: String, updated: Long, confirmedTotal: Long): Int
}
