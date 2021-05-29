package net.vicp.biggee.android.bOSMTracker.data

import net.vicp.biggee.android.bOSMTracker.Core
import net.vicp.biggee.android.osmtracker.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class CSV(imei: String) : File(File.createTempFile("tracker$imei", ".csv").toURI()), Callback {
    fun appendText(text: String, charset: Charset = Charsets.UTF_8) {
        text.split("\\n").forEach {
            OkHttpClient().newBuilder()
                .connectTimeout(1, TimeUnit.MINUTES)
                .callTimeout(1, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
                .newCall(
                    Request.Builder().url(BuildConfig.uploadUrl)
                        .post(toJsonString(it).toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                )
                .enqueue(this)
            return appendBytes(text.toByteArray(charset))
        }

    }

    private fun toJsonString(colData: String): String {
        val json = JSONObject()
        //组装Json
        val colNames = Core.head.split(",")
        val rowData = colData.split(",")
        colNames.forEachIndexed { i, colName ->
            json.put(colName, rowData[i])
        }
        return json.toString()
    }

    override fun onFailure(call: Call, e: IOException) {
        TODO("Not yet implemented")
    }

    override fun onResponse(call: Call, response: Response) {
        TODO("Not yet implemented")
    }
}