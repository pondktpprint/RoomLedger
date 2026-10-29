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
    @Query("SELECT * FROM bills WHERE roomId = :roomId AND billingMonth = :month LIMIT 1") suspend fun findBillForRoomMonth(roomId: String, month: String): BillEntity?
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
    @Query("UPDATE bills SET carriedDebtSatang=:carried, totalDueSatang=:due, paymentState=:state, updatedAtMillis=:updated WHERE id=:id") suspend fun updateFinancials(id: String, carried: Long, due: Long, state: String, updated: Long)
    @Query("UPDATE bills SET closedAtMillis=:closedAt, updatedAtMillis=:updated WHERE id=:id AND closedAtMillis IS NULL") suspend fun setClosed(id: String, closedAt: Long, updated: Long): Int
    @Query("SELECT * FROM payments WHERE billId=:billId ORDER BY paymentDateMillis DESC, createdAtMillis DESC") suspend fun paymentsForBill(billId: String): List<PaymentEntity>
    @Query("SELECT * FROM payments WHERE id=:id LIMIT 1") suspend fun payment(id: String): PaymentEntity?
    @Query("SELECT * FROM payment_allocations WHERE paymentId=:paymentId") suspend fun allocationsForPayment(paymentId: String): List<PaymentAllocationEntity>
    @Query("SELECT COALESCE(SUM(amountSatang), 0) FROM payment_allocations WHERE sourceBillId=:sourceBillId") suspend fun allocatedForSource(sourceBillId: String): Long
    @Query("SELECT * FROM debt_carry_forwards WHERE targetBillId=:billId ORDER BY targetMonth, sourceBillId") suspend fun carriesForBill(billId: String): List<DebtCarryForwardEntity>
    @Query("SELECT * FROM debt_carry_forwards WHERE roomId=:roomId AND targetMonth=:month AND targetBillId IS NULL ORDER BY sourceBillId") suspend fun pendingCarries(roomId: String, month: String): List<DebtCarryForwardEntity>
    @Query("SELECT * FROM debt_carry_forwards WHERE sourceBillId=:sourceBillId AND targetBillId IS NOT NULL ORDER BY targetMonth DESC LIMIT 1") suspend fun latestCarryForSource(sourceBillId: String): DebtCarryForwardEntity?
    @Query("SELECT DISTINCT bills.id FROM debt_carry_forwards INNER JOIN bills ON bills.id=debt_carry_forwards.targetBillId WHERE debt_carry_forwards.sourceBillId=:sourceBillId") suspend fun carriesForSourceTargets(sourceBillId: String): List<String>
    @Query("SELECT * FROM debt_carry_forwards WHERE sourceBillId=:sourceBillId AND targetMonth=:month LIMIT 1") suspend fun carryForSourceMonth(sourceBillId: String, month: String): DebtCarryForwardEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPayment(payment: PaymentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAllocations(allocations: List<PaymentAllocationEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCarry(carry: DebtCarryForwardEntity)
    @Query("UPDATE debt_carry_forwards SET targetBillId=:targetBillId, amountSatang=:amount, updatedAtMillis=:updated WHERE id=:id") suspend fun updateCarryTarget(id: String, targetBillId: String, amount: Long, updated: Long)
    @Query("UPDATE debt_carry_forwards SET amountSatang=:amount, updatedAtMillis=:updated WHERE id=:id") suspend fun updateCarryAmount(id: String, amount: Long, updated: Long)
}
