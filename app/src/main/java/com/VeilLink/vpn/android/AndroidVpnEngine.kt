package com.veillink.vpn.android

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.veillink.vpn.common.domain.VpnEngine
import com.veillink.vpn.common.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

class AndroidVpnEngine(private val appContext: Context) : VpnEngine {

    //Пробрасываем состояние тоннеля
    override val state: StateFlow<ConnectionState> get() = SingBoxService.state
    override fun start(configText: String) {
        Log.d("AndroidVpnEngine", "start() called, config length=${configText.length}")
        //Просто создаем Intent и вызываем метод сервиса
        val i = Intent(appContext, SingBoxService::class.java)
            .setAction(SingBoxService.ACTION_CONNECT)
            .putExtra(SingBoxService.CONFIG, configText)
        ContextCompat.startForegroundService(appContext, i)
    }

    override fun stop() {
        Log.d("AndroidVpnEngine", "stop() called")
        val i = Intent(appContext, SingBoxService::class.java)
            .setAction(SingBoxService.ACTION_DISCONNECT)
        ContextCompat.startForegroundService(appContext, i)
    }
}