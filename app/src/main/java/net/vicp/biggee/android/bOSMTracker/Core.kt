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
import androidx.room.Room
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
        val db = Room.databaseBuilder(applicationContext, DataCenter::class.java, "bOSMTrack").build()
        val setting = db.dao().getSetting()
        return setting.toTypedArray()
    }

    fun saveSetting(applicationContext: Context, setting: Setting, deadLine: Long = Long.MIN_VALUE) {
        val db = Room.databaseBuilder(applicationContext, DataCenter::class.java, "bOSMTrack").build()
        if (deadLine > 0) {
            db.dao().clearSetting(deadLine)
        }
        db.dao().addSetting(setting)
    }

    fun readSettingHistory(applicationContext: Context, dateBefore: Long): Array<Setting> {
        val db = Room.databaseBuilder(applicationContext, DataCenter::class.java, "bOSMTrack").build()
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

        val dataCols = mapOf(Pair(TrackContentProvider.Schema.COL_TRACK_ID, "序号"),
                Pair(TrackContentProvider.Schema.COL_TIMESTAMP, "记录时间"),
                Pair(TrackContentProvider.Schema.COL_LONGITUDE, "经度"),
                Pair(TrackContentProvider.Schema.COL_LATITUDE, "纬度"))
        var head = "设备标识,名字,开始时间,追踪组," + dataCols.values.joinToString(",")
        csv.appendText("$head\n", Charset.forName("GB18030"))

        //获取任务记录
        val trackersPoints = LongSparseArray<LongSparseArray<Map<String, String>>>()
        readTracker.keyIterator().forEach {
            val trackerPoints = readTrackerPoint(applicationContext, it)
            trackersPoints.put(it, trackerPoints)

            //组装html
            html.append(printTable(trackerPoints))
            //组装excel
            head = "$imei,${trackers[it].values.joinToString(",")}"
            trackerPoints.valueIterator().forEach { row ->
                val data = StringBuilder(head)
                dataCols.keys.iterator().forEach { colName ->
                    data.append(",${row[colName]}")
                }
                csv.appendText("$data\n")
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


}