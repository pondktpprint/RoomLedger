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
}
