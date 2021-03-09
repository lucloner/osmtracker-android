package net.vicp.biggee.android.bOSMTracker.db

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.concurrent.Executors

@Entity
data class DeviceON(@PrimaryKey var timeStamp: Long = System.currentTimeMillis(),
                    var intentAction: String = "",
                    var point_timestamp: Long = 0) : BroadcastReceiver() {

    @Transient
    @Ignore
    @JvmSynthetic
    var now = 0L
    override fun onReceive(context: Context?, intent: Intent?) {
        now++
        if (now < System.currentTimeMillis()) {
            now = System.currentTimeMillis()
        }
        timeStamp = now
        intentAction = "${intent?.action?.split(".")?.last()}"
        point_timestamp = trackPointTimeStamp

        Log.d(this::class.simpleName, "=DeviceON=$this")

        Executors.newWorkStealingPool().execute {
            try {
                DataCenter.getDB(context ?: return@execute)
                        .dao().addDeviceON(this)
            } catch (_: Exception) {
            }
        }
    }


    companion object {
        @Volatile
        var trackPointTimeStamp = 0L
    }
}
