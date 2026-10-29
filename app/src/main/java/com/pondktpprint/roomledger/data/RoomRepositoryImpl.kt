package com.pondktpprint.roomledger.data

import com.pondktpprint.roomledger.data.local.RoomDao
import com.pondktpprint.roomledger.data.local.toDomain
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.data.local.BillingDao
import com.pondktpprint.roomledger.data.local.BillEntity
import com.pondktpprint.roomledger.data.local.MeterReadingEntity
import androidx.room.withTransaction
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import com.pondktpprint.roomledger.domain.BillingCalculator
import com.pondktpprint.roomledger.domain.MeterInput
import com.pondktpprint.roomledger.domain.MonthlyBill
import com.pondktpprint.roomledger.domain.PaymentState
import com.pondktpprint.roomledger.domain.ReadingDraft
import java.util.UUID
import com.pondktpprint.roomledger.domain.RoomRecord
import com.pondktpprint.roomledger.domain.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRepositoryImpl(private val roomDao: RoomDao, private val billingDao: BillingDao, private val database: RoomLedgerDatabase? = null) : RoomRepository {
    override fun observeRooms(): Flow<List<RoomRecord>> =
        roomDao.observeAll().map { rooms -> rooms.map { it.toDomain() } }

    override suspend fun saveRoom(room: RoomRecord) {
        roomDao.upsert(room.toEntity())
    }

    override suspend fun setRoomActive(id: String, isActive: Boolean, updatedAtMillis: Long) {
        roomDao.setActive(id, isActive, updatedAtMillis)
    }

    override suspend fun billsForMonth(month: String) = billingDao.billsForMonth(month).map(BillEntity::toDomain)
    override suspend fun billHistory(roomId: String) = billingDao.history(roomId).map(BillEntity::toDomain)
    override suspend fun priorReading(roomId: String, month: String): ReadingDraft? = billingDao.priorReading(roomId, month)?.let { ReadingDraft(roomId, month, it.waterCurrent, it.waterCurrent, 0, it.electricityCurrent, it.electricityCurrent, 0) }
    override suspend fun laterReadingCount(roomId: String, month: String) = billingDao.laterReadingCount(roomId, month)

    override suspend fun saveReading(room: com.pondktpprint.roomledger.domain.RoomRecord, draft: ReadingDraft, confirmedLaterImpact: Boolean, updatedAtMillis: Long): MonthlyBill {
        require(draft.roomId == room.id && draft.billingMonth.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) { "รอบบิลไม่ถูกต้อง" }
        val db = database ?: error("Database transaction unavailable")
        return db.withTransaction {
        val prior = billingDao.reading(room.id, draft.billingMonth)
        require(prior != null || billingDao.billsForMonth(draft.billingMonth).none { it.roomId == room.id }) { "มีรายการของรอบบิลนี้แล้ว" }
        require(billingDao.laterReadingCount(room.id, draft.billingMonth) == 0 || confirmedLaterImpact) { "การแก้ไขเดือนย้อนหลังอาจกระทบยอดตั้งต้นของเดือนถัดไป กรุณายืนยันก่อน" }
        val existingBill = prior?.let { billingDao.findBillForReading(it.id) }
        val pricingRoom = if (existingBill == null) room else room.copy(name = existingBill.roomName, monthlyRentSatang = existingBill.rentSatang, waterPricePerUnitSatang = existingBill.waterRateSatang, electricityPricePerUnitSatang = existingBill.electricityRateSatang)
        val calc = BillingCalculator.calculate(pricingRoom, MeterInput(draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment), MeterInput(draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment))
        require(existingBill?.paymentState != PaymentState.PAID.name || existingBill.totalSatang == calc.totalSatang) { "บิลนี้ชำระแล้ว ยอดเปลี่ยนแปลง กรุณากระทบยอดและยืนยันสถานะการชำระใหม่ก่อนแก้ไข" }
        val reading = prior ?: MeterReadingEntity(UUID.randomUUID().toString(), room.id, draft.billingMonth, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, updatedAtMillis, updatedAtMillis)
        val bill = BillEntity(existingBill?.id ?: UUID.randomUUID().toString(), reading.id, room.id, pricingRoom.name, draft.billingMonth, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, pricingRoom.waterPricePerUnitSatang, calc.waterSatang, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, pricingRoom.electricityPricePerUnitSatang, calc.electricitySatang, calc.rentSatang, calc.totalSatang, existingBill?.paymentState ?: PaymentState.UNPAID.name, existingBill?.createdAtMillis ?: updatedAtMillis, updatedAtMillis)
        if (prior == null) billingDao.insertReading(reading) else billingDao.updateReading(reading.id, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, updatedAtMillis)
        if (existingBill == null) billingDao.insertBill(bill) else billingDao.updateBill(bill.id, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, calc.waterSatang, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, calc.electricitySatang, calc.rentSatang, calc.totalSatang, bill.paymentState, updatedAtMillis)
        bill.toDomain()
        }
    }

    override suspend fun reconcilePayment(bill: MonthlyBill, state: PaymentState, updatedAtMillis: Long) {
        check(billingDao.reconcilePayment(bill.id, state.name, updatedAtMillis, bill.totalSatang) == 1) { "ยอดบิลเปลี่ยนแล้ว กรุณาตรวจสอบและยืนยันยอดใหม่ก่อนบันทึกการชำระ" }
    }
}

private fun BillEntity.toDomain() = MonthlyBill(id, roomId, roomName, billingMonth, waterPrevious, waterCurrent, waterAdjustment, waterUnits, waterRateSatang, waterChargeSatang, electricityPrevious, electricityCurrent, electricityAdjustment, electricityUnits, electricityRateSatang, electricityChargeSatang, rentSatang, totalSatang, PaymentState.valueOf(paymentState), createdAtMillis, updatedAtMillis)
