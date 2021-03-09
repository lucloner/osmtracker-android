@file:Suppress("DEPRECATION")

package net.vicp.biggee.android.bOSMTracker

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.icu.text.SimpleDateFormat
import android.util.Log
import android.util.LongSparseArray
import androidx.core.util.forEach
import androidx.core.util.isEmpty
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import com.sun.mail.smtp.SMTPTransport
import net.osmtracker.db.TrackContentProvider
import net.vicp.biggee.android.bOSMTracker.db.DataCenter
import net.vicp.biggee.android.bOSMTracker.db.Setting
import net.vicp.biggee.android.osmtracker.BuildConfig
import java.io.*
import java.nio.charset.Charset
import java.util.*
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.activation.DataHandler
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos


object Core {
    var stopActiveTrack = Runnable { }
    val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE) }

    fun sendEmail(to: String,
                  subject: String = "JavaMail APIs Test",
                  message: String = "JavaMail APIs Test Hello World",
                  attachment: File? = null,
                  debug: Boolean = true): Boolean {
        val from: String = BuildConfig.smtpUser
        val host: String = BuildConfig.smtpServer

        // create some properties and get the default Session
        val props = Properties()
        props["mail.smtp.host"] = host
        if (debug) props["mail.debug"] = debug
        props["mail.smtp.auth"] = "true"

        val session: Session = Session.getInstance(props, null)
        session.debug = debug

        try {
            // create a message
            val msg = MimeMessage(session)
            msg.setFrom(from)
            val address = to.split(";")
                    .parallelStream()
                    .map { InternetAddress(it) }
                    .collect(Collectors.toList())
                    .toTypedArray()
            msg.setRecipients(Message.RecipientType.TO, address)
            msg.subject = subject
            msg.sentDate = Date()
            // If the desired charset is known, you can use
            // setText(text, charset)
            val html = "<HTML><HEAD><TITLE>${msg.subject}</TITLE></HEAD><BODY>$message</BODY></HTML>"
            Log.d("sendEmail", html)

            // create and fill the first message part
            val mbp1 = MimeBodyPart()
            mbp1.dataHandler = DataHandler(ByteArrayDataSource(html, "text/html"))

            // create the second message part
            val mbp2 = MimeBodyPart()
            if (attachment != null) {
                mbp2.attachFile(attachment)
            }

            /*
	         * Use the following approach instead of the above line if
	         * you want to control the MIME type of the attached file.
	         * Normally you should never need to do this.
	         *
	        FileDataSource fds = new FileDataSource(filename) {
		        public String getContentType() {
		            return "application/octet-stream";
		        }
	        };
	        mbp2.setDataHandler(new DataHandler(fds));
	        mbp2.setFileName(fds.getName());
	        */

            // create the Multipart and add its parts to it
            val mp: Multipart = MimeMultipart()
            mp.addBodyPart(mbp1)
            mp.addBodyPart(mbp2)
            // add the Multipart to the message
            msg.setContent(mp)

            msg.setHeader("X-Mailer", "sendhtml")
            val t = session.getTransport("smtp") as SMTPTransport
            t.connect(host, from, BuildConfig.smtpPass)
            t.sendMessage(msg, msg.allRecipients)
            t.close()
        } catch (mex: MessagingException) {
            Log.e(this::class.simpleName, "email sent failed!" + mex.message + mex.stackTraceToString())
            return false
        }
        Log.d(this::class.simpleName, "email sent!")
        return true
    }

    fun readTracker(context: Context, dateRange: LongRange = LongRange(Long.MIN_VALUE, Long.MAX_VALUE), limit: Long = Long.MAX_VALUE): LongSparseArray<Map<String, String>> {
        val contentResolver = ContextWrapper(context).contentResolver
        val idCol = 0
        val readCols = arrayOf(TrackContentProvider.Schema.COL_NAME,
                TrackContentProvider.Schema.COL_START_DATE,
                TrackContentProvider.Schema.COL_ID)
        val data = LongSparseArray<Map<String, String>>()

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                    TrackContentProvider.CONTENT_URI_TRACK, null, null, null,
                    TrackContentProvider.Schema.COL_START_DATE + " desc")
            while (cursor!!.moveToNext()) {
                val row = LinkedHashMap<String, String>()
                val id = cursor.getLong(idCol)
                readCols.iterator().forEach { row[it] = cursor.getString(cursor.getColumnIndex(it)) }
                val date = row[TrackContentProvider.Schema.COL_START_DATE]?.toLong() ?: continue
                if (dateRange.contains(date)) {
                    row[TrackContentProvider.Schema.COL_START_DATE] = dateFormat.format(date)
                    data.put(id, row)
                } else if (dateRange.first > date || data.size() >= limit) {
                    break
                }
            }
            return data
        } catch (e: Exception) {
            data.append(Long.MAX_VALUE, HashMap<String, String>().apply {
                put(readCols[0], "${e.message}")
            })
        } finally {
            cursor?.close()
        }
        return data
    }

    private fun readTrackerPoint(context: Context, trackerId: Long = 1, dateRange: LongRange = LongRange(Long.MIN_VALUE, Long.MAX_VALUE)): LongSparseArray<Map<String, String>> {
        val contentResolver = ContextWrapper(context).contentResolver
        val data = LongSparseArray<Map<String, String>>()
        val importantCols = arrayOf(TrackContentProvider.Schema.COL_LATITUDE, TrackContentProvider.Schema.COL_LONGITUDE)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                    TrackContentProvider.trackPointsUri(trackerId),
                    null, null, null, TrackContentProvider.Schema.COL_TIMESTAMP + " asc")
            val readCols = cursor!!.columnNames
            while (cursor.moveToNext()) {
                val row = LinkedHashMap<String, String>()
                val id = cursor.getLong(0)
                for (index in 1 until cursor.columnCount) {
                    if (importantCols.contains(readCols[index])) {
                        row[readCols[index]] = cursor.getDouble(index).toString()
                    } else {
                        row[readCols[index]] = "" + cursor.getString(index)
                    }
                }
                val date = row[TrackContentProvider.Schema.COL_TIMESTAMP]?.toLong() ?: continue
                if (dateRange.contains(date)) {
                    val intentAction = DataCenter.getDB(context).dao().getDeviceON(date)?.intentAction
                            ?: ""
                    row["intentAction"] = intentAction
                    row[TrackContentProvider.Schema.COL_TIMESTAMP] = dateFormat.format(date)
                    data.put(id, row)
                } else if (dateRange.last < date) {
                    break
                }
            }
            return data
        } catch (e: Exception) {
            data.append(Long.MAX_VALUE, HashMap<String, String>().apply {
                put(TrackContentProvider.Schema.COL_TRACK_ID, "${e.message}")
            })
        } finally {
            cursor?.close()
        }
        return data
    }

    private fun printTable(data: LongSparseArray<Map<String, String>>, table: String = "<table border=\"1\"><tr><td>", tr: String = "</td></tr><tr><td>", td: String = "</td><td>", closeTable: String = "</td></tr></table>"): String {
        if (data.isEmpty()) {
            return ""
        }
        val tableBody = StringBuilder(table)
        tableBody.append(data.valueAt(0).keys.joinToString(td))
                .append(tr)
        data.valueIterator().forEach {
            tableBody.append(it.values.toTypedArray().joinToString(td))
                    .append(tr)
        }

        return tableBody.append(closeTable).toString()
    }

    class FormattedDate(year: Int, month: Int) : Date(year, month, 1) {
        override fun toString(): String {
            return "${if (year < 1900) year + 1900 else year}年${month + 1}月"
        }
    }

    fun readSetting(applicationContext: Context): Array<Setting> {
        val db = DataCenter.getDB(applicationContext)
        val setting = db.dao().getSetting()
        return setting.toTypedArray()
    }

    fun saveSetting(applicationContext: Context, setting: Setting, deadLine: Long = Long.MIN_VALUE) {
        val db = DataCenter.getDB(applicationContext)
        if (deadLine > 0) {
            db.dao().clearSetting(deadLine)
        }
        db.dao().addSetting(setting)
    }

    fun readSettingHistory(applicationContext: Context, dateBefore: Long): Array<Setting> {
        val db = DataCenter.getDB(applicationContext)
        val setting = db.dao().getSettingHistory(dateBefore)
        return setting.toTypedArray()
    }

    fun assembleMailMessage(applicationContext: Context, months: Set<Date>, imei: String): Pair<String, File?> {
        val sorted = months.sorted()
        val last = sorted.last()
        val startDate = sorted.first().time
        val endDate = Date(last.year, last.month + 1, 1).time
        val dateRange = LongRange(startDate, endDate)
        val readTracker = readTracker(applicationContext, dateRange)
        val trackers = LongSparseArray<Map<String, String>>()

        //筛选追踪任务
        readTracker.forEach { id, row ->
            val date = row[TrackContentProvider.Schema.COL_START_DATE] ?: return@forEach
            val realDate = dateFormat.parse(date)
            val formatDate = Date(realDate.year, realDate.month, 1)
            if (months.parallelStream().anyMatch(formatDate::equals)) {
                trackers.put(id, row)
            }
            Log.d("trackers", "($months)=$formatDate:$id:$row")
        }

        //创建html
        val html = StringBuilder(printTable(trackers))
        //创建excel文件
        val csv = File.createTempFile("tracker$imei", ".csv")

        //找不到返回
        if (trackers.isEmpty()) {
            return Pair<String, File?>("找不到符合条件的记录", null)
        }

        val dataCols = mapOf(Pair(TrackContentProvider.Schema.COL_TIMESTAMP, "记录时间"),
                Pair(TrackContentProvider.Schema.COL_LONGITUDE, "经度"),
                Pair(TrackContentProvider.Schema.COL_LATITUDE, "纬度"),
                Pair("intentAction", "屏幕状态"))
        var head = "序号,设备标识,名字,开始时间,追踪组," + dataCols.values.joinToString(",")
        csv.appendText("$head\n", Charset.forName("GB18030"))

        //获取任务记录
        var cnt = 1

        readTracker.keyIterator().forEach {
            val trackerPoints = readTrackerPoint(applicationContext, it)

            val chkRangeStub = AreaRang(0.0, 0.0, -1.0)
            var chkRange = chkRangeStub
            var lastLine = ""
            var lastAction = ""

            //组装html
            html.append(printTable(trackerPoints))
            //组装excel
            head = "$imei,${trackers[it].values.joinToString(",")}"
            trackerPoints.valueIterator().forEach row@{ row ->
                val data = StringBuilder(head)
                dataCols.keys.iterator().forEach { colName ->
                    data.append(",${row[colName]}")
                }

                //处理屏幕开关
                val intentAction = row["intentAction"] ?: ""
                if (intentAction.isNotBlank()) {
                    //屏幕变化
                    if (lastAction != intentAction) {
                        chkRange = chkRangeStub
                    }
                    lastAction = intentAction

                } else {
                    data.append(lastAction)
                }

                val line = "${cnt++},$data\n"
                //处理距离
                val lat = row[TrackContentProvider.Schema.COL_LATITUDE]?.toDoubleOrNull()
                        ?: return@row
                val lon = row[TrackContentProvider.Schema.COL_LONGITUDE]?.toDoubleOrNull()
                        ?: return@row
                if (chkRange.testInRange(lat, lon)) {
                    lastLine = line
                    return@row
                } else if (lastLine.isNotBlank()) {
                    csv.appendText(lastLine)
                }
                chkRange = AreaRang(lat, lon, 10.0)
                csv.appendText(line)
                lastLine = ""
            }

            if (lastLine.isNotBlank()) {
                csv.appendText(lastLine)
            }
        }

        return Pair<String, File>(html.toString(), csv)
    }

    fun zipFile(file: File?): File? {
        file ?: return null
        val zipFile = File.createTempFile("csv", ".zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { fOut ->
            file.inputStream().buffered().use { fIn ->
                val entry = ZipEntry(file.name)
                fOut.putNextEntry(entry)
                fIn.copyTo(fOut)
            }
        }
        return zipFile
    }

    class AreaRang(private val latitude: Double, private val longitude: Double, private val range: Double) {
        private val longitudeToKilometers: Double = 111132.92
        private val latitudeToKilometers: Double = 111412.84

        /*      https://en.wikipedia.org/wiki/Geographic_coordinate_system
                On the WGS84 spheroid, the length in meters of a degree of latitude at latitude φ (that is, the number of meters you would have to travel along a north–south line to move 1 degree in latitude, when at latitude φ), is about
                111132.92 − 559.82 cos ⁡ 2 φ + 1.175 cos ⁡ 4 φ − 0.0023 cos ⁡ 6 φ {\displaystyle 111132.92-559.82\,\cos 2\varphi +1.175\,\cos 4\varphi -0.0023\,\cos 6\varphi } 111132.92-559.82\,\cos 2\varphi +1.175\,\cos 4\varphi -0.0023\,\cos 6\varphi [10]

                Similarly, the length in meters of a degree of longitude can be calculated as
                111412.84 cos ⁡ φ − 93.5 cos ⁡ 3 φ + 0.118 cos ⁡ 5 φ {\displaystyle 111412.84\,\cos \varphi -93.5\,\cos 3\varphi +0.118\,\cos 5\varphi } {\displaystyle 111412.84\,\cos \varphi -93.5\,\cos 3\varphi +0.118\,\cos 5\varphi }[10]

                所以距离只是和纬度有关
        */
        private val latitudeRad = PI * latitude / 180
        val metersPerLongitude = longitudeToKilometers * cos(latitudeRad) - cos(3 * latitudeRad) * 93.5 + cos(latitudeRad * 5) * 0.118
        val metersPerLatitude = latitudeToKilometers - cos(latitudeRad * 2) * 559.82 + cos(latitudeRad * 4) * 1.175 - cos(latitudeRad * 6) * 0.0023

        fun testInRange(latitude: Double, longitude: Double) = abs(latitude.minus(this.latitude).times(metersPerLatitude)) < range
                && abs(longitude.minus(this.longitude).times(metersPerLongitude)) < range
    }

    @Suppress("USELESS_ELVIS", "unused")
    object TestSuit {
        fun testDeviceON(applicationContext: Context) {
            val db = DataCenter.getDB(applicationContext).dao()
            Log.e("testDeviceON", "1===" + db.getDeviceON(0))
            Log.e("testDeviceON", "2===" + db.getDeviceON(-1) ?: "non")
        }

        fun deleteInvalid(applicationContext: Context) = DataCenter.getDB(applicationContext).dao().delDeviceON(0)
    }
}