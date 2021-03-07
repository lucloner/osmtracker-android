package net.vicp.biggee.android.bOSMTracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Setting(@PrimaryKey val timeStamp: Long = System.currentTimeMillis(),
                   val email: String,
                   val sentDate: Long,
                   val repeatTime: Long,
                   val imei: String,
                   val cron: Boolean = true)
