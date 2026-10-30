package com.pondktpprint.roomledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RoomGroupEntity::class, RoomEntity::class, MeterReadingEntity::class, BillEntity::class, PaymentEntity::class, PaymentAllocationEntity::class, DebtCarryForwardEntity::class], version = 4, exportSchema = true)
abstract class RoomLedgerDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun roomGroupDao(): RoomGroupDao
    abstract fun billingDao(): BillingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS meter_readings (id TEXT NOT NULL PRIMARY KEY, roomId TEXT NOT NULL, billingMonth TEXT NOT NULL, waterPrevious INTEGER NOT NULL, waterCurrent INTEGER NOT NULL, waterAdjustment INTEGER NOT NULL, waterUnits INTEGER NOT NULL, electricityPrevious INTEGER NOT NULL, electricityCurrent INTEGER NOT NULL, electricityAdjustment INTEGER NOT NULL, electricityUnits INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_meter_readings_roomId_billingMonth ON meter_readings(roomId, billingMonth)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meter_readings_roomId ON meter_readings(roomId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS bills (id TEXT NOT NULL PRIMARY KEY, readingId TEXT NOT NULL, roomId TEXT NOT NULL, roomName TEXT NOT NULL, billingMonth TEXT NOT NULL, waterPrevious INTEGER NOT NULL, waterCurrent INTEGER NOT NULL, waterAdjustment INTEGER NOT NULL, waterUnits INTEGER NOT NULL, waterRateSatang INTEGER NOT NULL, waterChargeSatang INTEGER NOT NULL, electricityPrevious INTEGER NOT NULL, electricityCurrent INTEGER NOT NULL, electricityAdjustment INTEGER NOT NULL, electricityUnits INTEGER NOT NULL, electricityRateSatang INTEGER NOT NULL, electricityChargeSatang INTEGER NOT NULL, rentSatang INTEGER NOT NULL, totalSatang INTEGER NOT NULL, paymentState TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(readingId) REFERENCES meter_readings(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bills_roomId_billingMonth ON bills(roomId, billingMonth)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bills_readingId ON bills(readingId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_roomId ON bills(roomId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN carriedDebtSatang INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN totalDueSatang INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN closedAtMillis INTEGER DEFAULT NULL")
                db.execSQL("UPDATE bills SET totalDueSatang = totalSatang")
                db.execSQL("CREATE TABLE IF NOT EXISTS payments (id TEXT NOT NULL PRIMARY KEY, billId TEXT NOT NULL, amountSatang INTEGER NOT NULL, paymentDateMillis INTEGER NOT NULL, note TEXT, entryType TEXT NOT NULL, reversesPaymentId TEXT, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, FOREIGN KEY(billId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(reversesPaymentId) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_billId ON payments(billId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payments_reversesPaymentId ON payments(reversesPaymentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_paymentDateMillis ON payments(paymentDateMillis)")
                db.execSQL("CREATE TABLE IF NOT EXISTS payment_allocations (id TEXT NOT NULL PRIMARY KEY, paymentId TEXT NOT NULL, sourceBillId TEXT NOT NULL, amountSatang INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, FOREIGN KEY(paymentId) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(sourceBillId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_allocations_paymentId_sourceBillId ON payment_allocations(paymentId, sourceBillId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_allocations_sourceBillId ON payment_allocations(sourceBillId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS debt_carry_forwards (id TEXT NOT NULL PRIMARY KEY, sourceBillId TEXT NOT NULL, roomId TEXT NOT NULL, targetMonth TEXT NOT NULL, targetBillId TEXT, amountSatang INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(sourceBillId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(targetBillId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_debt_carry_forwards_sourceBillId_targetMonth ON debt_carry_forwards(sourceBillId, targetMonth)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_carry_forwards_targetBillId ON debt_carry_forwards(targetBillId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_carry_forwards_roomId_targetMonth ON debt_carry_forwards(roomId, targetMonth)")
                val paidBills = mutableListOf<Triple<String, Long, Long>>()
                db.query("SELECT id, totalSatang, updatedAtMillis FROM bills WHERE paymentState = 'PAID'").use { cursor -> while (cursor.moveToNext()) paidBills += Triple(cursor.getString(0), cursor.getLong(1), cursor.getLong(2)) }
                for ((billId, amount, timestamp) in paidBills) {
                    if (amount <= 0L) continue
                    val paymentId = java.util.UUID.randomUUID().toString()
                    db.execSQL("INSERT INTO payments (id,billId,amountSatang,paymentDateMillis,note,entryType,reversesPaymentId,createdAtMillis,updatedAtMillis) VALUES (?,?,?,?,?,'RECEIPT',NULL,?,?)", arrayOf(paymentId, billId, amount, timestamp, "นำเข้าสถานะชำระเดิม", timestamp, timestamp))
                    db.execSQL("INSERT INTO payment_allocations (id,paymentId,sourceBillId,amountSatang,createdAtMillis) VALUES (?,?,?,?,?)", arrayOf(java.util.UUID.randomUUID().toString(), paymentId, billId, amount, timestamp))
                }
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS room_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_room_groups_sortOrder_name ON room_groups(sortOrder, name)")
                db.execSQL("ALTER TABLE rooms ADD COLUMN groupId TEXT DEFAULT NULL REFERENCES room_groups(id) ON UPDATE NO ACTION ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_groupId ON rooms(groupId)")
                db.query("PRAGMA foreign_key_check").use { cursor -> check(!cursor.moveToNext()) { "พบข้อมูลอ้างอิงฐานข้อมูลเดิมที่ไม่ถูกต้อง" } }
            }
        }
    }
}
