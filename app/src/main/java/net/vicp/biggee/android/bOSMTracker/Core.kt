package net.vicp.biggee.android.bOSMTracker

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.util.Log
import android.util.LongSparseArray
import androidx.core.util.isNotEmpty
import androidx.core.util.valueIterator
import com.sun.mail.smtp.SMTPTransport
import net.osmtracker.db.TrackContentProvider
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

    fun sendEmail(to: String, message: String = "JavaMail APIs Test", debug: Boolean = true): Boolean {
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
            msg.subject = "JavaMail APIs Test"
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
        val readCols = arrayOf("name", "start_date")
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
        val tableBody = StringBuilder(table)
        if (data.isNotEmpty()) {
            tableBody.append(data.valueAt(0).keys.joinToString(td))
                    .append(tr)
        }
        data.valueIterator().forEach {
            tableBody.append(it.values.toTypedArray().joinToString(td))
                    .append(tr)
        }

        return tableBody.append(closeTable).toString()
    }
}