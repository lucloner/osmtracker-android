package net.vicp.biggee.android.bOSMTracker

import android.util.Log
import com.sun.mail.smtp.SMTPTransport
import net.vicp.biggee.android.osmtracker.BuildConfig
import java.util.*
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage


object Core {
    fun sendEmail(to: String, debug: Boolean = true): Boolean {
        val from: String = BuildConfig.smtpUser
        val host: String = BuildConfig.smtpServer

        // create some properties and get the default Session

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
            msg.setFrom(InternetAddress(from))
            val address = arrayOf(InternetAddress(to))
            msg.setRecipients(Message.RecipientType.TO, address)
            msg.subject = "JavaMail APIs Test"
            msg.sentDate = Date()
            // If the desired charset is known, you can use
            // setText(text, charset)
            msg.setText("Hello World")

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
}