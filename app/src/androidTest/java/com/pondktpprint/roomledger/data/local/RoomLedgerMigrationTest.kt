package com.pondktpprint.roomledger.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class RoomLedgerMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun createVersionOneDatabaseAndMigrate() {
        helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE rooms (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, monthlyRentSatang INTEGER NOT NULL, waterPricePerUnitSatang INTEGER NOT NULL, electricityPricePerUnitSatang INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        db = helper.writableDatabase
        db.execSQL("INSERT INTO rooms VALUES ('legacy-room', 'ห้องเดิม', 1200000, 1800, 700, 1, 10, 20)")
        RoomLedgerDatabase.MIGRATION_1_2.migrate(db)
    }

    @After fun closeDb() { helper.close() }

    @Test fun migrationPreservesExistingRoomsAndCreatesPhaseTwoTables() {
        db.query("SELECT name, monthlyRentSatang FROM rooms WHERE id='legacy-room'").use { cursor ->
            assertEquals(true, cursor.moveToFirst()); assertEquals("ห้องเดิม", cursor.getString(0)); assertEquals(1_200_000L, cursor.getLong(1))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('meter_readings','bills') ORDER BY name").use { cursor -> assertEquals(2, cursor.count) }
    }

    @Test fun duplicateRoomAndMonthReadingIsRejectedByUniqueConstraint() {
        db.execSQL("INSERT INTO meter_readings VALUES ('m1','legacy-room','2026-01',1,2,0,1,1,2,0,1,30,30)")
        assertThrows(SQLiteConstraintException::class.java) { db.execSQL("INSERT INTO meter_readings VALUES ('m2','legacy-room','2026-01',2,3,0,1,2,3,0,1,40,40)") }
    }

    @Test fun versionTwoMigrationPreservesPaidBillsAndCreatesAuditableReceipt() {
        val v2 = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE rooms (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, monthlyRentSatang INTEGER NOT NULL, waterPricePerUnitSatang INTEGER NOT NULL, electricityPricePerUnitSatang INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE meter_readings (id TEXT NOT NULL PRIMARY KEY, roomId TEXT NOT NULL, billingMonth TEXT NOT NULL, waterPrevious INTEGER NOT NULL, waterCurrent INTEGER NOT NULL, waterAdjustment INTEGER NOT NULL, waterUnits INTEGER NOT NULL, electricityPrevious INTEGER NOT NULL, electricityCurrent INTEGER NOT NULL, electricityAdjustment INTEGER NOT NULL, electricityUnits INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, FOREIGN KEY(roomId) REFERENCES rooms(id) ON DELETE NO ACTION ON UPDATE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX index_meter_readings_roomId_billingMonth ON meter_readings(roomId,billingMonth)")
                db.execSQL("CREATE INDEX index_meter_readings_roomId ON meter_readings(roomId)")
                db.execSQL("CREATE TABLE bills (id TEXT NOT NULL PRIMARY KEY, readingId TEXT NOT NULL, roomId TEXT NOT NULL, roomName TEXT NOT NULL, billingMonth TEXT NOT NULL, waterPrevious INTEGER NOT NULL, waterCurrent INTEGER NOT NULL, waterAdjustment INTEGER NOT NULL, waterUnits INTEGER NOT NULL, waterRateSatang INTEGER NOT NULL, waterChargeSatang INTEGER NOT NULL, electricityPrevious INTEGER NOT NULL, electricityCurrent INTEGER NOT NULL, electricityAdjustment INTEGER NOT NULL, electricityUnits INTEGER NOT NULL, electricityRateSatang INTEGER NOT NULL, electricityChargeSatang INTEGER NOT NULL, rentSatang INTEGER NOT NULL, totalSatang INTEGER NOT NULL, paymentState TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, FOREIGN KEY(roomId) REFERENCES rooms(id) ON DELETE NO ACTION ON UPDATE NO ACTION, FOREIGN KEY(readingId) REFERENCES meter_readings(id) ON DELETE NO ACTION ON UPDATE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX index_bills_roomId_billingMonth ON bills(roomId,billingMonth)")
                db.execSQL("CREATE UNIQUE INDEX index_bills_readingId ON bills(readingId)")
                db.execSQL("CREATE INDEX index_bills_roomId ON bills(roomId)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        try {
            val old = v2.writableDatabase
            old.execSQL("INSERT INTO rooms VALUES ('phase2-room','ห้องเฟสสอง',500000,100,50,1,10,20)")
            old.execSQL("INSERT INTO meter_readings VALUES ('phase2-reading','phase2-room','2025-12',10,15,0,5,20,25,0,5,10,20)")
            old.execSQL("INSERT INTO bills VALUES ('phase2-bill','phase2-reading','phase2-room','ห้องเฟสสอง','2025-12',10,15,0,5,100,500,20,25,0,5,50,250,500000,500750,'PAID',10,20)")
            RoomLedgerDatabase.MIGRATION_2_3.migrate(old)
            old.query("SELECT totalSatang,totalDueSatang,carriedDebtSatang,closedAtMillis,paymentState FROM bills WHERE id='phase2-bill'").use { cursor ->
                assertEquals(true, cursor.moveToFirst()); assertEquals(500_750L, cursor.getLong(0)); assertEquals(500_750L, cursor.getLong(1)); assertEquals(0L, cursor.getLong(2)); assertEquals(true, cursor.isNull(3)); assertEquals("PAID", cursor.getString(4))
            }
            old.query("SELECT billId,amountSatang,entryType FROM payments").use { cursor -> assertEquals(true, cursor.moveToFirst()); assertEquals("phase2-bill", cursor.getString(0)); assertEquals(500_750L, cursor.getLong(1)); assertEquals("RECEIPT", cursor.getString(2)) }
            old.query("SELECT sourceBillId,amountSatang FROM payment_allocations").use { cursor -> assertEquals(true, cursor.moveToFirst()); assertEquals("phase2-bill", cursor.getString(0)); assertEquals(500_750L, cursor.getLong(1)) }
            old.query("SELECT name FROM rooms WHERE id='phase2-room'").use { cursor -> assertEquals(true, cursor.moveToFirst()); assertEquals("ห้องเฟสสอง", cursor.getString(0)) }
            old.query("SELECT waterCurrent FROM meter_readings WHERE id='phase2-reading'").use { cursor -> assertEquals(true, cursor.moveToFirst()); assertEquals(15L, cursor.getLong(0)) }
        } finally { v2.close() }
    }
}
