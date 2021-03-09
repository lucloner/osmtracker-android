package net.vicp.biggee.android.bOSMTracker.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DataAccess {
    @Query("SELECT * FROM setting ORDER BY timeStamp DESC LIMIT :size")
    fun getSetting(size: Int = 3): List<Setting>

    @Insert
    fun addSetting(setting: Setting)

    @Query("DELETE FROM setting WHERE timeStamp<:deadLine")
    fun clearSetting(deadLine: Long)

    @Query("SELECT * FROM setting WHERE sentDate<:dateBefore ORDER BY timeStamp DESC LIMIT :size")
    fun getSettingHistory(dateBefore: Long, size: Int = 3): List<Setting>

    @Insert
    fun addDeviceON(deviceON: DeviceON)

    @Query("SELECT * FROM deviceON WHERE :trackPointTimeStamp BETWEEN point_timestamp AND timeStamp ORDER BY timeStamp LIMIT 1")
    fun getDeviceON(trackPointTimeStamp: Long): DeviceON?
}