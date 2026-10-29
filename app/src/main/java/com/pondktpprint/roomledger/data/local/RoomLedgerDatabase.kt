package com.pondktpprint.roomledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RoomEntity::class], version = 1, exportSchema = true)
abstract class RoomLedgerDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
}
