package net.vicp.biggee.android.bOSMTracker.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Setting::class, DeviceON::class], version = 4)
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
                },
                        object : Migration(2, 3) {
                            override fun migrate(database: SupportSQLiteDatabase) {
                                database.execSQL("ALTER TABLE DeviceON ADD COLUMN wifiName TEXT NOT NULL DEFAULT ''")
                                database.execSQL("ALTER TABLE DeviceON ADD COLUMN gsmOperate TEXT NOT NULL DEFAULT ''")
                                database.execSQL("ALTER TABLE DeviceON ADD COLUMN gsmCell TEXT NOT NULL DEFAULT ''")
                                database.execSQL("ALTER TABLE DeviceON ADD COLUMN gsmLocation TEXT NOT NULL DEFAULT ''")
                            }
                        },
                        object : Migration(3, 4) {
                            override fun migrate(database: SupportSQLiteDatabase) {
                                database.execSQL("ALTER TABLE DeviceON ADD COLUMN inDoorLocation INTEGER NOT NULL DEFAULT 0")
                            }
                        }
                ).build()
    }
}