package com.pondktpprint.roomledger.domain

import kotlinx.coroutines.flow.Flow

/** Storage boundary; a future backup implementation can be added without coupling the UI to Firebase. */
interface RoomRepository {
    fun observeRooms(): Flow<List<RoomRecord>>
    fun observeRoomGroups(): Flow<List<RoomGroup>>
    suspend fun saveRoom(room: RoomRecord)
    suspend fun setRoomActive(id: String, isActive: Boolean, updatedAtMillis: Long)
    suspend fun createRoomGroup(name: String, nowMillis: Long): RoomGroup
    suspend fun renameRoomGroup(id: String, name: String, nowMillis: Long)
    suspend fun deleteRoomGroup(id: String, nowMillis: Long)
    suspend fun assignRoomToGroup(roomId: String, groupId: String?, nowMillis: Long)
    suspend fun roomHasFinancialHistory(roomId: String): Boolean
    suspend fun deleteRoomPermanently(roomId: String): RoomDeletionResult
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
