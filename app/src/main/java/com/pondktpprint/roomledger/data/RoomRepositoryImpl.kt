package com.pondktpprint.roomledger.data

import com.pondktpprint.roomledger.data.local.RoomDao
import com.pondktpprint.roomledger.data.local.toDomain
import com.pondktpprint.roomledger.data.local.toEntity
import com.pondktpprint.roomledger.data.local.BillingDao
import com.pondktpprint.roomledger.data.local.BillEntity
import com.pondktpprint.roomledger.data.local.MeterReadingEntity
import com.pondktpprint.roomledger.data.local.PaymentEntity
import com.pondktpprint.roomledger.data.local.PaymentAllocationEntity
import com.pondktpprint.roomledger.data.local.DebtCarryForwardEntity
import androidx.room.withTransaction
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase
import com.pondktpprint.roomledger.domain.BillingCalculator
import com.pondktpprint.roomledger.domain.MeterInput
import com.pondktpprint.roomledger.domain.MonthlyBill
import com.pondktpprint.roomledger.domain.PaymentState
import com.pondktpprint.roomledger.domain.ReadingDraft
import com.pondktpprint.roomledger.domain.PaymentEntryType
import com.pondktpprint.roomledger.domain.PaymentRecord
import com.pondktpprint.roomledger.domain.BillFinancialSummary
import java.time.YearMonth
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
    override suspend fun billForRoomMonth(roomId: String, month: String) = billingDao.billsForMonth(month).firstOrNull { it.roomId == roomId }?.toDomain()
    override suspend fun billHistory(roomId: String) = billingDao.history(roomId).map(BillEntity::toDomain)
    override suspend fun priorReading(roomId: String, month: String): ReadingDraft? = billingDao.priorReading(roomId, month)?.let { ReadingDraft(roomId, month, it.waterCurrent, it.waterCurrent, 0, it.electricityCurrent, it.electricityCurrent, 0) }
    override suspend fun readingForMonth(roomId: String, month: String): ReadingDraft? = billingDao.reading(roomId, month)?.let { ReadingDraft(roomId, month, it.waterPrevious, it.waterCurrent, it.waterAdjustment, it.electricityPrevious, it.electricityCurrent, it.electricityAdjustment) }
    override suspend fun laterReadingCount(roomId: String, month: String) = billingDao.laterReadingCount(roomId, month)

    override suspend fun saveReading(room: com.pondktpprint.roomledger.domain.RoomRecord, draft: ReadingDraft, confirmedLaterImpact: Boolean, updatedAtMillis: Long): MonthlyBill {
        require(draft.roomId == room.id && draft.billingMonth.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) { "รอบบิลไม่ถูกต้อง" }
        val db = database ?: error("Database transaction unavailable")
        return db.withTransaction {
        val prior = billingDao.reading(room.id, draft.billingMonth)
        require(prior != null || billingDao.billsForMonth(draft.billingMonth).none { it.roomId == room.id }) { "มีรายการของรอบบิลนี้แล้ว" }
        require(billingDao.laterReadingCount(room.id, draft.billingMonth) == 0 || confirmedLaterImpact) { "การแก้ไขเดือนย้อนหลังอาจกระทบยอดตั้งต้นของเดือนถัดไป กรุณายืนยันก่อน" }
        val existingBill = prior?.let { billingDao.findBillForReading(it.id) }
        require(existingBill?.closedAtMillis == null) { "บิลปิดแล้ว ไม่สามารถแก้ไขยอดได้" }
        require(existingBill == null || billingDao.paymentsForBill(existingBill.id).isEmpty()) { "บิลมีประวัติรับเงินแล้ว กรุณาแก้ไขผ่านรายการกระทบยอด" }
        require(existingBill?.paymentState != PaymentState.PAID.name) { "บิลที่ชำระแล้วต้องกระทบยอดผ่านรายการรับเงินก่อนแก้ไข" }
        val pricingRoom = if (existingBill == null) room else room.copy(name = existingBill.roomName, monthlyRentSatang = existingBill.rentSatang, waterPricePerUnitSatang = existingBill.waterRateSatang, electricityPricePerUnitSatang = existingBill.electricityRateSatang)
        val calc = BillingCalculator.calculate(pricingRoom, MeterInput(draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment), MeterInput(draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment))
        val billId = existingBill?.id ?: UUID.randomUUID().toString()
        val pendingCarries = if (existingBill == null) billingDao.pendingCarries(room.id, draft.billingMonth) else emptyList()
        var carried = existingBill?.carriedDebtSatang ?: 0L
        if (existingBill == null) {
            carried = pendingCarries.fold(0L) { sum, carry -> Math.addExact(sum, sourceOutstanding(carry.sourceBillId)) }
        }
        val totalDue = Math.addExact(calc.totalSatang, carried)
        val reading = prior ?: MeterReadingEntity(UUID.randomUUID().toString(), room.id, draft.billingMonth, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, updatedAtMillis, updatedAtMillis)
        val bill = BillEntity(billId, reading.id, room.id, pricingRoom.name, draft.billingMonth, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, pricingRoom.waterPricePerUnitSatang, calc.waterSatang, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, pricingRoom.electricityPricePerUnitSatang, calc.electricitySatang, calc.rentSatang, calc.totalSatang, carried, totalDue, existingBill?.closedAtMillis, existingBill?.paymentState ?: PaymentState.UNPAID.name, existingBill?.createdAtMillis ?: updatedAtMillis, updatedAtMillis)
        if (prior == null) billingDao.insertReading(reading) else billingDao.updateReading(reading.id, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, updatedAtMillis)
        if (existingBill == null) {
            billingDao.insertBill(bill)
            pendingCarries.forEach { carry -> billingDao.updateCarryTarget(carry.id, bill.id, sourceOutstanding(carry.sourceBillId), updatedAtMillis) }
        } else {
            billingDao.updateBill(bill.id, draft.waterPrevious, draft.waterCurrent, draft.waterAdjustment, calc.waterUnits, calc.waterSatang, draft.electricityPrevious, draft.electricityCurrent, draft.electricityAdjustment, calc.electricityUnits, calc.electricitySatang, calc.rentSatang, calc.totalSatang, PaymentState.UNPAID.name, updatedAtMillis)
            billingDao.updateFinancials(bill.id, carried, totalDue, PaymentState.UNPAID.name, updatedAtMillis)
        }
        bill.toDomain()
        }
    }

    override suspend fun financialSummary(billId: String): BillFinancialSummary {
        val bill = billingDao.findBill(billId) ?: error("ไม่พบบิล")
        val sources = componentSources(bill)
        val outstanding = sources.fold(0L) { total, source -> Math.addExact(total, sourceOutstanding(source)) }
        val billed = bill.totalDueSatang
        val received = (billed - outstanding).coerceAtLeast(0L)
        return BillFinancialSummary(bill.toDomain(), billed, received, outstanding, billingDao.paymentsForBill(billId).map(PaymentEntity::toDomain), bill.closedAtMillis != null, bill.closedAtMillis)
    }

    override suspend fun recordPayment(billId: String, amountSatang: Long, paymentDateMillis: Long, note: String?, nowMillis: Long): PaymentRecord {
        require(amountSatang > 0) { "จำนวนเงินที่รับต้องมากกว่า 0" }
        require(note == null || note.length <= 500) { "หมายเหตุต้องไม่เกิน 500 ตัวอักษร" }
        val db = database ?: error("Database transaction unavailable")
        return db.withTransaction {
            val bill = billingDao.findBill(billId) ?: error("ไม่พบบิล")
            val sources = componentSources(bill).sortedBy { billingDao.findBill(it)?.billingMonth ?: "" }
            val balances = mutableMapOf<String, Long>()
            for (source in sources) balances[source] = sourceOutstanding(source)
            val totalOutstanding = balances.values.fold(0L) { total, amount -> Math.addExact(total, amount) }
            require(amountSatang <= totalOutstanding) { "จำนวนเงินรับเกินยอดคงเหลือ" }
            val payment = PaymentEntity(UUID.randomUUID().toString(), billId, amountSatang, paymentDateMillis, note?.trim()?.takeIf(String::isNotEmpty), PaymentEntryType.RECEIPT.name, null, nowMillis, nowMillis)
            billingDao.insertPayment(payment)
            var remaining = amountSatang
            val allocations = mutableListOf<PaymentAllocationEntity>()
            for (source in sources) {
                if (remaining == 0L) break
                val allocation = minOf(remaining, balances.getValue(source))
                if (allocation > 0) allocations += PaymentAllocationEntity(UUID.randomUUID().toString(), payment.id, source, allocation, nowMillis)
                remaining -= allocation
            }
            billingDao.insertAllocations(allocations)
            refreshProjections(sources, nowMillis)
            refreshPaymentStates(sources + billId, nowMillis)
            payment.toDomain()
        }
    }

    override suspend fun reversePayment(paymentId: String, note: String?, nowMillis: Long): PaymentRecord {
        require(note == null || note.length <= 500) { "หมายเหตุต้องไม่เกิน 500 ตัวอักษร" }
        val db = database ?: error("Database transaction unavailable")
        return db.withTransaction {
            val original = billingDao.payment(paymentId) ?: error("ไม่พบรายการรับเงิน")
            require(original.entryType == PaymentEntryType.RECEIPT.name) { "เลือกรายการรับเงินที่ยังไม่ถูกกลับรายการ" }
            require(billingDao.paymentsForBill(original.billId).none { it.reversesPaymentId == original.id }) { "รายการนี้ถูกกลับรายการแล้ว" }
            val reversal = PaymentEntity(UUID.randomUUID().toString(), original.billId, original.amountSatang, nowMillis, note?.trim()?.takeIf(String::isNotEmpty) ?: "กลับรายการรับเงิน ${original.id}", PaymentEntryType.REVERSAL.name, original.id, nowMillis, nowMillis)
            billingDao.insertPayment(reversal)
            val reversedAllocations = billingDao.allocationsForPayment(original.id).map { item -> PaymentAllocationEntity(UUID.randomUUID().toString(), reversal.id, item.sourceBillId, -item.amountSatang, nowMillis) }
            billingDao.insertAllocations(reversedAllocations)
            val sources = reversedAllocations.map(PaymentAllocationEntity::sourceBillId).distinct()
            for (source in sources) {
                val sourceBill = billingDao.findBill(source) ?: continue
                if (sourceBill.closedAtMillis != null && sourceOutstanding(source) > 0) ensureCarryForward(sourceBill, nowMillis)
            }
            refreshProjections(sources, nowMillis)
            refreshPaymentStates(sources + original.billId, nowMillis)
            reversal.toDomain()
        }
    }

    override suspend fun closeBill(billId: String, nowMillis: Long) {
        val db = database ?: error("Database transaction unavailable")
        db.withTransaction {
            val bill = billingDao.findBill(billId) ?: error("ไม่พบบิล")
            require(bill.closedAtMillis == null) { "บิลนี้ปิดแล้ว" }
            val nextMonth = YearMonth.parse(bill.billingMonth).plusMonths(1).toString()
            val target = billingDao.findBillForRoomMonth(bill.roomId, nextMonth)
            require(target?.closedAtMillis == null) { "บิลเดือนถัดไปปิดแล้ว กรุณากระทบยอดรอบถัดไปก่อน" }
            val sources = componentSources(bill)
            for (source in sources) {
                val outstanding = sourceOutstanding(source)
                if (outstanding <= 0) continue
                val existing = billingDao.carryForSourceMonth(source, nextMonth)
                if (existing == null) {
                    val entry = DebtCarryForwardEntity(UUID.randomUUID().toString(), source, bill.roomId, nextMonth, target?.id, outstanding, nowMillis, nowMillis)
                    billingDao.insertCarry(entry)
                } else if (target != null) billingDao.updateCarryTarget(existing.id, target.id, outstanding, nowMillis)
                else billingDao.updateCarryAmount(existing.id, outstanding, nowMillis)
            }
            if (target != null) refreshBillProjection(target, nowMillis)
            check(billingDao.setClosed(billId, nowMillis, nowMillis) == 1) { "ปิดบิลไม่สำเร็จ" }
        }
    }

    private suspend fun ensureCarryForward(sourceBill: BillEntity, nowMillis: Long) {
        val latest = billingDao.latestCarryForSource(sourceBill.id)
        var targetMonth = latest?.targetMonth ?: YearMonth.parse(sourceBill.billingMonth).plusMonths(1).toString()
        val latestTargetId = latest?.targetBillId
        if (latestTargetId != null) {
            val previousTarget = billingDao.findBill(latestTargetId)
            if (previousTarget?.closedAtMillis != null) targetMonth = YearMonth.parse(targetMonth).plusMonths(1).toString()
        }
        var target = billingDao.findBillForRoomMonth(sourceBill.roomId, targetMonth)
        while (target?.closedAtMillis != null) {
            targetMonth = YearMonth.parse(targetMonth).plusMonths(1).toString()
            target = billingDao.findBillForRoomMonth(sourceBill.roomId, targetMonth)
        }
        val outstanding = sourceOutstanding(sourceBill.id)
        val existing = billingDao.carryForSourceMonth(sourceBill.id, targetMonth)
        if (existing == null) {
            val carry = DebtCarryForwardEntity(UUID.randomUUID().toString(), sourceBill.id, sourceBill.roomId, targetMonth, target?.id, outstanding, nowMillis, nowMillis)
            billingDao.insertCarry(carry)
        } else if (target != null) billingDao.updateCarryTarget(existing.id, target.id, outstanding, nowMillis)
        else billingDao.updateCarryAmount(existing.id, outstanding, nowMillis)
        if (target != null) refreshBillProjection(target, nowMillis)
    }

    private suspend fun componentSources(bill: BillEntity): List<String> = (billingDao.carriesForBill(bill.id).map(DebtCarryForwardEntity::sourceBillId) + bill.id).distinct()

    private suspend fun sourceOutstanding(sourceBillId: String): Long {
        val source = billingDao.findBill(sourceBillId) ?: error("ไม่พบบิลต้นทาง")
        val outstanding = Math.subtractExact(source.totalSatang, billingDao.allocatedForSource(sourceBillId))
        check(outstanding >= 0) { "ยอดจัดสรรเกินยอดบิลต้นทาง ต้องกระทบยอดก่อน" }
        return outstanding
    }

    private suspend fun refreshProjections(sources: List<String>, nowMillis: Long) {
        for (source in sources.distinct()) {
            val carry = billingDao.latestCarryForSource(source) ?: continue
            val targetId = carry.targetBillId ?: continue
            val target = billingDao.findBill(targetId) ?: continue
            if (target.closedAtMillis != null) continue
            billingDao.updateCarryAmount(carry.id, sourceOutstanding(source), nowMillis)
            refreshBillProjection(target, nowMillis)
        }
    }

    private suspend fun refreshBillProjection(target: BillEntity, nowMillis: Long) {
        val carries = billingDao.carriesForBill(target.id)
        var carried = 0L
        for (entry in carries) carried = Math.addExact(carried, sourceOutstanding(entry.sourceBillId))
        val due = Math.addExact(target.totalSatang, carried)
        val balance = (componentSources(target).fold(0L) { total, source -> Math.addExact(total, sourceOutstanding(source)) })
        billingDao.updateFinancials(target.id, carried, due, if (balance == 0L) PaymentState.PAID.name else PaymentState.UNPAID.name, nowMillis)
    }

    private suspend fun refreshPaymentStates(billIds: List<String>, nowMillis: Long) {
        val affected = billIds.toMutableSet()
        for (source in billIds.distinct()) affected += billingDao.carriesForSourceTargets(source)
        for (id in affected) billingDao.findBill(id)?.let { bill ->
            val balance = componentSources(bill).fold(0L) { total, source -> Math.addExact(total, sourceOutstanding(source)) }
            billingDao.updateFinancials(bill.id, bill.carriedDebtSatang, bill.totalDueSatang, if (balance == 0L) PaymentState.PAID.name else PaymentState.UNPAID.name, nowMillis)
        }
    }
}

private fun BillEntity.toDomain() = MonthlyBill(id, roomId, roomName, billingMonth, waterPrevious, waterCurrent, waterAdjustment, waterUnits, waterRateSatang, waterChargeSatang, electricityPrevious, electricityCurrent, electricityAdjustment, electricityUnits, electricityRateSatang, electricityChargeSatang, rentSatang, totalSatang, PaymentState.valueOf(paymentState), createdAtMillis, updatedAtMillis, carriedDebtSatang, totalDueSatang, closedAtMillis)
private fun PaymentEntity.toDomain() = PaymentRecord(id, billId, amountSatang, paymentDateMillis, note, PaymentEntryType.valueOf(entryType), reversesPaymentId, createdAtMillis, updatedAtMillis)
