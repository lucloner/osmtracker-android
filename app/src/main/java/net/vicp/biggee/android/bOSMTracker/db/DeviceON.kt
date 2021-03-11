package net.vicp.biggee.android.bOSMTracker.db

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.Runnable
import net.osmtracker.activity.TrackLogger
import pub.devrel.easypermissions.EasyPermissions
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Entity
data class DeviceON(@PrimaryKey var timeStamp: Long = System.currentTimeMillis(),
                    var intentAction: String = "",
                    var point_timestamp: Long = 0,
                    var wifiName: String = "",
                    var gsmOperate: String = "",
                    var gsmCell: String = "",
                    var gsmLocation: String = "",
                    var inDoorLocation: Long = 0) : BroadcastReceiver(), Runnable {

    override fun onReceive(context: Context?, intent: Intent?) {

        synchronized(timeStamp) {
            timeStamp = max(++timeStamp, System.currentTimeMillis())
        }

        if (inDoorLocation <= 0 && intentAction.isNotBlank() && intent?.action?.contains(intentAction) != false && point_timestamp == trackPointTimeStamp) {
            Log.d(this::class.simpleName, "=DeviceON=Duplicate $intent $trackPointTimeStamp $this")
            return
        }

        intentAction = "${intent?.action?.split(".")?.last()}"
        point_timestamp = trackPointTimeStamp

        Log.d(this::class.simpleName, "=DeviceON=$this")

        if (point_timestamp == 0L) {
            return
        }

        saveToDB(context)
    }

    private fun saveToDB(context: Context?) {
        if (context != null && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //获得基站资料
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?)?.apply {
                gsmOperate = networkOperator
                gsmCell = allCellInfo.joinToString(",")
            }
        }

        //获得WIFI资料
        (context?.getSystemService(Context.WIFI_SERVICE) as WifiManager?)?.connectionInfo?.apply {
            val ipStr = String.format("%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff)
            wifiName = "$ssid($bssid)$ipStr"
        }

        Executors.newWorkStealingPool().execute {
            try {
                DataCenter.getDB(context ?: return@execute)
                        .dao().addDeviceON(this)
            } catch (_: Exception) {
                timeStamp++
            }
        }
    }

    override fun run() {
        if (currentGpsActivity?.gpsLogger?.currentTrackId ?: -2 < 0) {
            executors?.shutdown()
            executors = null

            Log.e(this::class.simpleName, "executors shutdown:${currentGpsActivity?.gpsLogger?.currentTrackId ?: -3}")
            return
        }
        point_timestamp = System.currentTimeMillis()

        var location: Location? = null
        currentGpsActivity?.gpsLogger?.apply {
            arrayOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER).iterator().forEach {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return@forEach
                }
                location = location ?: lmgr.getLastKnownLocation(it)
            }
            inDoorLocation = currentTrackId

            gsmLocation = "${location?.latitude} ${location?.longitude}"

            synchronized(timeStamp) {
                timeStamp = max(++timeStamp, System.currentTimeMillis())
            }

            saveToDB(currentGpsActivity)

            currentGpsActivity?.runOnUiThread {
                Toast.makeText(this, "记录WIFI信息$wifiName", Toast.LENGTH_SHORT).show()
            }
        }

        Log.d(this::class.simpleName, "LocationManager:$location")
    }

    companion object {
        @Volatile
        var trackPointTimeStamp = 0L
        var currentGpsActivity: TrackLogger? = null

        fun queryPermissions(activity: Activity,
                             permissions: Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                                     Manifest.permission.INTERNET,
                                     Manifest.permission.ACCESS_WIFI_STATE,
                                     Manifest.permission.ACCESS_NETWORK_STATE,
                                     Manifest.permission.READ_PHONE_STATE,
                                     Manifest.permission.ACCESS_COARSE_LOCATION,
                                     Manifest.permission.CHANGE_WIFI_STATE,
                                     Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS)) {
            if (!EasyPermissions.hasPermissions(activity, *permissions)) {
                EasyPermissions.requestPermissions(activity, "尝试获取IMEI", 1, *permissions)
            }
        }

        private var executors: ScheduledExecutorService? = null

        fun doMonitor(deviceON: DeviceON) {
            executors?.shutdown()
            executors = Executors.newSingleThreadScheduledExecutor().apply {
                scheduleAtFixedRate(deviceON, 30, 10, TimeUnit.SECONDS)
            }
        }
    }
}
