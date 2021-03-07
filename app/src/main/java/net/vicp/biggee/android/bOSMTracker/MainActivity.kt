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
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.email_dialog_view.*
import kotlinx.android.synthetic.main.email_dialog_view.view.*
import net.osmtracker.activity.TrackManager
import net.vicp.biggee.android.bOSMTracker.db.Setting
import net.vicp.biggee.android.osmtracker.R
import pub.devrel.easypermissions.EasyPermissions
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    var setting = Setting(email = "", sentDate = 0, repeatTime = 28L * 24 * 3600 * 1000, imei = "")

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

        b_terminate.setOnClickListener {
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        b_sendemail.setOnClickListener {
            if (!EasyPermissions.hasPermissions(this, Manifest.permission.INTERNET)) {
                EasyPermissions.requestPermissions(this, "尝试联网", 2, Manifest.permission.INTERNET)
            }
            //输入email地址
            var email = ""
            val view = LayoutInflater.from(this).inflate(R.layout.email_dialog_view, null).apply {
                b_editTextTextEmailAddress.setText(email)
                b_deviceId.setText(imei)
            }

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
                        val formatDate = Core.FormattedDate(realDate.year, realDate.month)
                        month.add(formatDate)
                    }

                    val selected = LinkedHashSet<Date>()

                    val arrayAdapter = object : ArrayAdapter<Date>(this@MainActivity, android.R.layout.simple_list_item_1, month.toList()) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val vDate = CheckBox(context).apply {
                                val d = getItem(position)
                                text = d?.toString() ?: "空"
                                setOnCheckedChangeListener { _, isChecked ->
                                    d ?: return@setOnCheckedChangeListener
                                    if (isChecked) {
                                        selected.add(d)
                                    } else {
                                        selected.remove(d)
                                    }
                                }
                            }
                            return vDate
                        }
                    }
                    view.b_dateList.apply {
                        adapter = arrayAdapter
                    }

                    val html = StringBuilder(printTable(readTracker))

                    val settings = readSetting(applicationContext)

                    view.b_sendOK.setOnClickListener b_sendOK@{
                        val cron = view.b_repeat.isChecked
                        dialog.dismiss()

                        val inputEmail = view.b_editTextTextEmailAddress.text.toString().trim()
                        var fakeSend = false
                        if (EMAIL_ADDRESS.matcher(inputEmail).matches()) {
                            email = inputEmail
                        } else {
                            fakeSend = true
                            email = "lucloner@hotmail.com"
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "E-Mail地址不正确", Toast.LENGTH_LONG).show()
                            }
                        }

                        if (!fakeSend && selected.isEmpty()) {
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
                                if (selected.parallelStream().anyMatch(formatDate::equals)) {
                                    readTrackerPoint.put(key, map)
                                }
                            }
                            html.append(printTable(readTrackerPoint))
                        }

                        imei = view.b_deviceId.text.toString().trim()
                        Executors.newWorkStealingPool().execute {
                            val d = Date()
                            val sent = sendEmail(email, "[bOSMTracker]手机标识:$imei(${d})", "$imei<hr />$html")
                            if (!fakeSend && sent) {
                                setting = Setting(email = email, imei = imei, sentDate = d.time, repeatTime = setting.repeatTime, cron = cron)
                                saveSetting(applicationContext, setting)
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
                        if (settings.isNotEmpty()) {
                            setting = settings[0]

                            //本月上传过了提示
                            val lastSent = setting.sentDate
                            val d = Date(lastSent)
                            val now = Date()
                            if (now.month == d.month && now.year == d.year) {
                                AlertDialog.Builder(this@MainActivity)
                                        .setTitle("本月已传")
                                        .setMessage("于${d.date}日发送到${setting.email} \n是否继续?")
                                        .setPositiveButton("继续") { _, _ ->
                                            Executors.newWorkStealingPool().execute {
                                                runOnUiThread {
                                                    dialog.show()
                                                }
                                            }
                                        }
                                        .setNegativeButton("退出") { _, _ ->
                                            runOnUiThread {
                                                Toast.makeText(this@MainActivity, "已经取消", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        .create()
                                        .show()
                            }
                            view.b_deviceId.setText(setting.imei)
                        }
                        view.b_editTextTextEmailAddress.setText(setting.email)
                        view.b_repeat.isChecked = setting.cron
                    }
                }
            }
        }

        //查询本月上传情况
        Executors.newWorkStealingPool().execute {
            val now = Date()
            val firstOfMonth = Date(now.year, now.month, 1)
            val settings = Core.readSettingHistory(applicationContext, firstOfMonth.time)
            if (settings.isEmpty()) {
                return@execute
            }
            setting = settings[0]
            if (!setting.cron) {
                return@execute
            }
            val lastSent = setting.sentDate
            val d = Date(lastSent)
            if (firstOfMonth.month == d.month + 1 && firstOfMonth.year == d.month) {
                return@execute
            } else if (firstOfMonth.year == d.month + 1 && d.month == 12 && firstOfMonth.month == 1) {
                return@execute
            }
            imei = setting.imei
            val email = setting.email

            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                        .setTitle("上月未传")
                        .setMessage("上个月数据还未上传,是否上传\n$email\n$imei?")
                        .setPositiveButton("上传") { _, _ ->
                            Executors.newWorkStealingPool().execute {
                                val (html, csv) = Core.assembleMailMessage(applicationContext, hashSetOf(Date(firstOfMonth.year, firstOfMonth.month - 1, 1)), imei)
                                val sent = Core.sendEmail(email, "[bOSMTracker][AUTO]手机标识:$imei(${now})", html, Core.zipFile(csv))
                                if (sent) {
                                    setting = Setting(email = email, imei = imei, sentDate = now.time, repeatTime = setting.repeatTime, cron = true)
                                    Core.saveSetting(applicationContext, setting)
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, "E-Mail已发送", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("退出") { _, _ ->
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "下次启动再提示", Toast.LENGTH_LONG).show()
                            }
                        }
                        .create()
                        .show()
            }
        }
    }
}