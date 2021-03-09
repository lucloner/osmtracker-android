package net.vicp.biggee.android.bOSMTracker.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Setting::class, DeviceON::class], version = 2)
abstract class DataCenter : RoomDatabase() {
    abstract fun dao(): DataAccess

    companion object {
        fun getDB(applicationContext: Context) = Room.databaseBuilder(applicationContext, DataCenter::class.java, "bOSMTrack")
                .addMigrations(object : Migration(1, 2) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS DeviceON (
                                timeStamp INTEGER PRIMARY KEY NOT NULL,
                                point_timestamp INTEGER NOT NULL,
                                intentAction TEXT NOT NULL  
                            )""")
                    }
                }).build()
    }
}