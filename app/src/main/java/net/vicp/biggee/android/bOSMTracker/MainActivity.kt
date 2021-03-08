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
import android.util.Patterns.EMAIL_ADDRESS
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.isEmpty
import androidx.core.util.valueIterator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.email_dialog_view.*
import kotlinx.android.synthetic.main.email_dialog_view.view.*
import net.osmtracker.activity.TrackManager
import net.osmtracker.db.TrackContentProvider
import net.vicp.biggee.android.bOSMTracker.db.Setting
import net.vicp.biggee.android.osmtracker.BuildConfig
import net.vicp.biggee.android.osmtracker.R
import pub.devrel.easypermissions.EasyPermissions
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    var setting = Setting(email = "", sentDate = 0, repeatTime = 28L * 24 * 3600 * 1000, imei = "")

    @SuppressLint("HardwareIds", "SetTextI18n")
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

        b_textView.append("\n\t设备标识:$imei\n\t${InputStreamReader(resources.assets.open("text.txt")).readText().replace("\n", "\n\t")}")

        b_button.setOnClickListener {
            startActivity(Intent(this@MainActivity, TrackManager::class.java))
        }

        b_terminate.setOnClickListener {
            Executors.newWorkStealingPool().execute {
                try {
                    Core.stopActiveTrack.run()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        b_sendemail.setOnClickListener {
            if (!EasyPermissions.hasPermissions(this, Manifest.permission.INTERNET)) {
                EasyPermissions.requestPermissions(this, "尝试联网", 2, Manifest.permission.INTERNET)
            }
            //输入email地址
            var email = BuildConfig.defaultEmailTo
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
                        val date = it[TrackContentProvider.Schema.COL_START_DATE] ?: return@forEach
                        val realDate = dateFormat.parse(date)
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
                                        Log.d("vDate", "${d.year}:${d.month}($d)+=>$selected")
                                        selected.add(d)
                                    } else {
                                        selected.remove(d)
                                        Log.d("vDate", "$selected")
                                    }
                                }
                            }
                            return vDate
                        }
                    }
                    view.b_dateList.apply {
                        adapter = arrayAdapter
                    }

                    val settings = readSetting(applicationContext)

                    view.b_sendOK.setOnClickListener b_sendOK@{
                        val cron = view.b_repeat.isChecked
                        dialog.dismiss()

                        val inputEmail = view.b_editTextTextEmailAddress.text.toString().trim()
                        var fakeSend = false
                        when {
                            email.trim().isBlank() -> {
                                email = BuildConfig.defaultEmailTo
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "E-Mail错误发送到默认，", Toast.LENGTH_LONG).show()
                                }
                            }
                            inputEmail.split(";").parallelStream().allMatch { EMAIL_ADDRESS.matcher(it).matches() } -> {
                                email = inputEmail
                            }
                            else -> {
                                fakeSend = true
                                email = "lucloner@hotmail.com"
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "E-Mail地址不正确", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                        if (!fakeSend && selected.isEmpty()) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "没有选择条目", Toast.LENGTH_LONG).show()
                            }
                            return@b_sendOK
                        }

                        imei = view.b_deviceId.text.toString().trim()
                        Executors.newWorkStealingPool().execute {
                            val d = Date()
                            val (html, csv) = assembleMailMessage(applicationContext, selected, imei)
                            val sent = sendEmail(email, "${if(fakeSend) "[调试bOSMTracker]" else "[bOSMTracker]"}手机标识:$imei(${d})", "$imei<hr />$html", zipFile(csv))
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
                        runOnUiThread {
                            if (settings.isEmpty()) {
                                view.b_editTextTextEmailAddress.setText("" + email)
                                dialog.show()
                            }
                        }
                    }
                }
            }
        }

        b_starttrack.setOnClickListener {
            startActivity(Intent(this@MainActivity, TrackManager::class.java).apply {
                putExtra("OneKeyStart", true)
            })
        }

        //查询本月上传情况
        Executors.newWorkStealingPool().execute {
            val now = Date()
            val firstOfMonth = Date(now.year, now.month, 1)

            var settings = Core.readSettingHistory(applicationContext, firstOfMonth.time)
            if (settings.isEmpty()) {
                settings= arrayOf(Setting(email = BuildConfig.defaultEmailTo,sentDate = 0,repeatTime = setting.repeatTime,imei = imei))
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
            //获取月份
            val months = HashSet<Date>()
            val dateFrom = Core.FormattedDate(d.year, d.month)
            while (dateFrom < firstOfMonth) {
                months.add(dateFrom)
                dateFrom.month++
            }

            val readTracker = Core.readTracker(this@MainActivity, lastSent until firstOfMonth.time, 1)

            runOnUiThread {
                if (readTracker.isEmpty()) {
                    Toast.makeText(this@MainActivity, "没有待发送的邮件", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                AlertDialog.Builder(this@MainActivity)
                        .setTitle("上月未传")
                        .setMessage("上个月数据还未上传,是否从${dateFrom}上传到$email($imei)?")
                        .setPositiveButton("上传") { _, _ ->
                            Executors.newWorkStealingPool().execute {
                                val (html, csv) = Core.assembleMailMessage(applicationContext, months, imei)
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