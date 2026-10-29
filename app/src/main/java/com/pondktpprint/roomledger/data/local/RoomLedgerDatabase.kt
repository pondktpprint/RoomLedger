package com.pondktpprint.roomledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RoomEntity::class, MeterReadingEntity::class, BillEntity::class], version = 2, exportSchema = true)
abstract class RoomLedgerDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
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
    }
}
