package com.veillink.vpn.android

import android.content.Context
import android.content.SharedPreferences
import com.veillink.vpn.common.model.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit

/**
 * Простая Android-реализация TokenStore.
 */
class AndroidTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    override suspend fun setAccessToken(token: String?) = withContext(Dispatchers.IO) {
        prefs.edit {
            if (token == null) {
                remove(KEY_ACCESS_TOKEN)
            } else {
                putString(KEY_ACCESS_TOKEN, token)
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit { clear() }
    }

    private companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}