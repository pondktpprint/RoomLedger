package com.pondktpprint.roomledger

import android.app.Application
import androidx.room.Room
import com.pondktpprint.roomledger.data.RoomRepositoryImpl
import com.pondktpprint.roomledger.data.local.RoomLedgerDatabase

class RoomLedgerApplication : Application() {
    val repository by lazy {
        val database = Room.databaseBuilder(
            applicationContext,
            RoomLedgerDatabase::class.java,
            "room-ledger.db",
        ).addMigrations(RoomLedgerDatabase.MIGRATION_1_2, RoomLedgerDatabase.MIGRATION_2_3).build()
        RoomRepositoryImpl(database.roomDao(), database.billingDao(), database)
    }
}
