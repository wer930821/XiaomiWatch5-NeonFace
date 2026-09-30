package com.agoose.neonupdater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(UPDATER_INFO_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@withContext Result.retry()
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(body)
            val latestCode = json.getInt("updaterVersionCode")
            val latestName = json.optString("updaterVersionName")

            if (latestCode > BuildConfig.VERSION_CODE) {
                UpdateNotifier.notifyUpdaterUpdate(applicationContext, latestName)
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UPDATER_INFO_URL =
            "https://github.com/wer930821/XiaomiWatch5-NeonFace/releases/download/latest/update-info.json"
    }
}
