package net.vicp.biggee.android.bOSMTracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import net.osmtracker.activity.TrackManager
import net.vicp.biggee.android.osmtracker.R
import pub.devrel.easypermissions.EasyPermissions
import java.io.InputStreamReader
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var imei = "没有获取到IMEI"
        if (!EasyPermissions.hasPermissions(this, Manifest.permission.READ_PHONE_STATE)) {
            EasyPermissions.requestPermissions(this, "尝试获取IMEI", 1, Manifest.permission.READ_PHONE_STATE)
        }

        try {
            val tm = baseContext.getSystemService(Service.TELEPHONY_SERVICE) as TelephonyManager
            imei = when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> {
                    Settings.System.getString(contentResolver, Settings.Secure.ANDROID_ID)
                }
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                    tm.imei
                }
                else -> {
                    tm.deviceId
                }
            }
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "$e${e.stackTraceToString()}");
        }

        b_textView.append("\t设备标识:$imei\n\t${InputStreamReader(resources.assets.open("text.txt")).readText().replace("\n", "\n\t")}")

        b_button.setOnClickListener {
            startActivity(Intent(this@MainActivity, TrackManager::class.java))
        }

        b_sendemail.setOnClickListener {
            if (!EasyPermissions.hasPermissions(this, Manifest.permission.INTERNET)) {
                EasyPermissions.requestPermissions(this, "尝试联网", 2, Manifest.permission.INTERNET)
            }
            Executors.newSingleThreadExecutor().execute {
                Core.apply {
                    sendEmail("lucloner@hotmail.com", printTable(readTracker(this@MainActivity)) + printTable(readTrackerPoint(this@MainActivity, 1)), true)
                }
            }
        }
    }
}