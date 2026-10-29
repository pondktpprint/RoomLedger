package com.pondktpprint.roomledger.domain

import kotlinx.coroutines.flow.Flow

/** Storage boundary; a future backup implementation can be added without coupling the UI to Firebase. */
interface RoomRepository {
    fun observeRooms(): Flow<List<RoomRecord>>
    suspend fun saveRoom(room: RoomRecord)
    suspend fun setRoomActive(id: String, isActive: Boolean, updatedAtMillis: Long)
    suspend fun billsForMonth(month: String): List<MonthlyBill>
    suspend fun billForRoomMonth(roomId: String, month: String): MonthlyBill?
    suspend fun billHistory(roomId: String): List<MonthlyBill>
    suspend fun priorReading(roomId: String, month: String): ReadingDraft?
    suspend fun readingForMonth(roomId: String, month: String): ReadingDraft?
    suspend fun laterReadingCount(roomId: String, month: String): Int
    suspend fun saveReading(room: RoomRecord, draft: ReadingDraft, confirmedLaterImpact: Boolean, updatedAtMillis: Long): MonthlyBill
    suspend fun financialSummary(billId: String): BillFinancialSummary
    suspend fun recordPayment(billId: String, amountSatang: Long, paymentDateMillis: Long, note: String?, nowMillis: Long): PaymentRecord
    suspend fun reversePayment(paymentId: String, note: String?, nowMillis: Long): PaymentRecord
    suspend fun closeBill(billId: String, nowMillis: Long)
}
