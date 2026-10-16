package com.hackathon.apprisk.data.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.hackathon.apprisk.data.storage.AppStorage
import com.hackathon.apprisk.domain.model.AppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class HashLookupResult(
    val sha256: String,
    val status: String,
    val detail: String,
)

object ApkHashService {
    suspend fun sha256(context: Context, app: AppRecord): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("apk-hashes", Context.MODE_PRIVATE)
        val key = "${app.packageName}_${app.lastUpdateTime}"
        prefs.getString(key, null)?.let { return@withContext it }
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getApplicationInfo(
                app.packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(app.packageName, 0)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(info.sourceDir).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        prefs.edit().putString(key, hash).apply()
        hash
    }

    suspend fun lookup(context: Context, hash: String, endpoint: String): HashLookupResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 7_000
                connection.readTimeout = 7_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val body = "query=get_info&hash=${URLEncoder.encode(hash, "UTF-8")}"
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (responseCode !in 200..299) {
                    HashLookupResult(hash, "unavailable", "Lookup unavailable (HTTP $responseCode).")
                } else {
                    val json = JSONObject(response)
                    val queryStatus = json.optString("query_status")
                    when (queryStatus) {
                        "ok" -> HashLookupResult(hash, "match", "The fingerprint is listed by MalwareBazaar.")
                        "no_results" -> HashLookupResult(hash, "clean", "No match found in MalwareBazaar.")
                        else -> HashLookupResult(hash, "unavailable", "Lookup unavailable: $queryStatus")
                    }
                }
            } catch (_: Exception) {
                HashLookupResult(hash, "unavailable", "Lookup unavailable offline. The SHA-256 fingerprint stays on this device.")
            }
        }

    fun consented(context: Context): Boolean = AppStorage(context).hashLookupConsented()
}
