package net.vicp.biggee.android.bOSMTracker.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = arrayOf(Setting::class), version = 1)
abstract class DataCenter : RoomDatabase() {
    abstract fun dao(): DataAccess
}