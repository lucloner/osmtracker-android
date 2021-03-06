package net.vicp.biggee.android.bOSMTracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.util.LongSparseArray
import android.util.Patterns.EMAIL_ADDRESS
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.email_dialog_view.*
import kotlinx.android.synthetic.main.email_dialog_view.view.*
import net.osmtracker.activity.TrackManager
import net.vicp.biggee.android.osmtracker.R
import pub.devrel.easypermissions.EasyPermissions
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet


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
            //输入email地址
            var email = "lucloner@hotmail.com"
            val view = LayoutInflater.from(this).inflate(R.layout.email_dialog_view, null)
            val dialog = AlertDialog.Builder(this)
                    .setView(view)
                    .create()

            Executors.newWorkStealingPool().execute {
                Core.apply {
                    val readTracker = readTracker(this@MainActivity)
                    val month = LinkedHashSet<Date>()
                    readTracker.valueIterator().forEach {
                        val date = it["start_date"] ?: return@forEach
                        val realDate = Date(date.toLong())
                        val formatDate = Core.FormattedDate(1900 + realDate.year, realDate.month)
                        month.add(formatDate)
                    }
                    val arrayAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, month.toList())

                    view.b_dateList.adapter = arrayAdapter

                    val html = StringBuilder(printTable(readTracker))

                    view.b_sendOK.setOnClickListener b_sendOK@{
                        dialog.dismiss()

                        val inputEmail = view.b_editTextTextEmailAddress.text.toString()
                        var fakeSend = false
                        if (EMAIL_ADDRESS.matcher(inputEmail).matches()) {
                            email = inputEmail
                        } else {
                            fakeSend = true
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "E-Mail地址z不正确", Toast.LENGTH_LONG).show()
                            }
                        }

                        month.clear()
                        view.b_dateList.checkedItemPositions.forEach { pos, checked ->
                            if (checked) {
                                val selectDate = arrayAdapter.getItem(pos) ?: return@forEach
                                month.add(selectDate)
                                Log.d(this@MainActivity::class.simpleName, "选择了$selectDate")
                            }
                        }

                        if (month.isEmpty()) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "没有选择条目", Toast.LENGTH_LONG).show()
                            }
                            return@b_sendOK
                        }

                        readTracker.keyIterator().forEach {
                            val readTrackerPoint = LongSparseArray<Map<String, String>>()

                            readTrackerPoint(this@MainActivity, it).forEach readTrackerPoint@{ key, map ->
                                val date = map["point_timestamp"] ?: return@readTrackerPoint
                                val realDate = Date(date.toLong())
                                val formatDate = Date(realDate.year, realDate.month, 1)
                                if (month.parallelStream().anyMatch(formatDate::equals)) {
                                    readTrackerPoint.put(key, map)
                                }
                            }
                            html.append(printTable(readTrackerPoint))
                        }

                        Executors.newWorkStealingPool().execute {
                            val sent = sendEmail(email, "[bOSMTracker]手机标识:$imei(${Date()})", html.toString(), true)
                            if (!fakeSend && sent) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "E-Mail已发送", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    view.b_sendCancel.setOnClickListener {
                        dialog.dismiss()
                    }

                    runOnUiThread {
                        dialog.show()
                    }
                }
            }
        }
    }
}