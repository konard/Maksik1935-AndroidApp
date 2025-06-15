package com.VeilLink.androidapp.network

import com.VeilLink.androidapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class VpnApiService(private val client: OkHttpClient = OkHttpClient()) {
    private val baseUrl = "https://${BuildConfig.VPN_SERVER_NAME}"

    suspend fun login(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url("${baseUrl}/login")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    suspend fun getServers(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}/getServersList")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList<String>()
            val json = response.body?.string() ?: return@use emptyList<String>()
            val arr = JSONArray(json)
            List(arr.length()) { idx -> arr.getString(idx) }
        }
    }

    suspend fun getConfig(server: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}/getConfig?server=${server}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use ""
            response.body?.string() ?: ""
        }
    }
}
