package net.vicp.biggee.android.bOSMTracker

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.util.Log
import android.util.LongSparseArray
import androidx.core.util.isEmpty
import androidx.core.util.valueIterator
import androidx.room.Room
import com.sun.mail.smtp.SMTPTransport
import net.osmtracker.db.TrackContentProvider
import net.vicp.biggee.android.bOSMTracker.db.DataCenter
import net.vicp.biggee.android.bOSMTracker.db.Setting
import net.vicp.biggee.android.osmtracker.BuildConfig
import java.util.*
import javax.activation.DataHandler
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.util.ByteArrayDataSource
import kotlin.collections.HashMap
import kotlin.collections.set


object Core {

    fun sendEmail(to: String, subject: String = "JavaMail APIs Test", message: String = "JavaMail APIs Test Hello World", debug: Boolean = true): Boolean {
        val from: String = BuildConfig.smtpUser
        val host: String = BuildConfig.smtpServer

        // create some properties and get the default Session
        val props = Properties()
        props.put("mail.smtp.host", host)
        if (debug) props["mail.debug"] = debug
        props["mail.smtp.auth"] = "true";

        val session: Session = Session.getInstance(props, null)
        session.debug = debug

        try {
            // create a message
            val msg = MimeMessage(session)
            msg.setFrom(from)
            val address = arrayOf(InternetAddress(to))
            msg.setRecipients(Message.RecipientType.TO, address)
            msg.subject = subject
            msg.sentDate = Date()
            // If the desired charset is known, you can use
            // setText(text, charset)
            val html = "<HTML><HEAD><TITLE>${msg.subject}</TITLE></HEAD><BODY>$message</BODY></HTML>"

            Log.d("sendEmail", html)
            msg.dataHandler = DataHandler(ByteArrayDataSource(html, "text/html"))

            msg.setHeader("X-Mailer", "sendhtml");
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

    fun readTracker(context: Context): LongSparseArray<Map<String, String>> {
        val contentResolver = ContextWrapper(context).contentResolver
        val idCol = 0
        val readCols = arrayOf("name", "start_date", "_id")
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
                data.put(id, row)
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

    fun readTrackerPoint(context: Context, trackerId: Long): LongSparseArray<Map<String, String>> {
        val contentResolver = ContextWrapper(context).contentResolver
        val idColName = "track_id"

        val data = LongSparseArray<Map<String, String>>()

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
                    row[readCols[index]] = "" + cursor.getString(index)
                }
                data.put(id, row)
            }
            return data
        } catch (e: Exception) {
            data.append(Long.MAX_VALUE, HashMap<String, String>().apply {
                put(idColName, "${e.message}")
            })
        } finally {
            cursor?.close()
        }
        return data
    }

    fun printTable(data: LongSparseArray<Map<String, String>>, table: String = "<table border=\"1\"><tr><td>", tr: String = "</td></tr><tr><td>", td: String = "</td><td>", closeTable: String = "</td></tr></table>"): String {
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
}