package com.veillink.vpn.common.control

import com.veillink.vpn.common.domain.ConfigProvider
import com.veillink.vpn.common.domain.VpnEngine
import com.veillink.vpn.common.model.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Мини‑оркестратор: получает готовый конфиг как строку и передаёт его в движок.
 */
class VpnController(
    private val engine: VpnEngine,
    private val configs: ConfigProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    //Пробрасываем состояние тоннеля
    val state: StateFlow<ConnectionState> get() = engine.state

    // Текущая операция подключения
    @Volatile
    private var connectJob: Job? = null

    //команда на коннект, в отдельном потоке, с блокировкой
    fun connect() {
        android.util.Log.d(this.javaClass.canonicalName, "connect() called")
        scope.launch {
            // Если уже идёт connect — сначала отменим его и дождёмся окончания
            connectJob?.cancelAndJoin()
            connectJob = coroutineContext.job
            try {
                android.util.Log.d(this.javaClass.canonicalName, "Fetching config...")
                val cfg = configs.fetchConfigText()
                android.util.Log.d(this.javaClass.canonicalName, "Config fetched, length=${cfg.length}")
                engine.start(cfg)
            } catch (e: CancellationException) {
                android.util.Log.d(this.javaClass.canonicalName, "connect() cancelled", e)
                // отмена (по disconnect/новому connect)
                runCatching { engine.stop() }
                // не логируем как error
            } catch (e: Throwable) {
                android.util.Log.e(this.javaClass.canonicalName, "connect() failed", e)
                runCatching { engine.stop() }
            } finally {
                //Очищаем ссылку
                if (connectJob === coroutineContext.job) {
                    connectJob = null
                }
            }
        }
    }


    fun disconnect() {
        android.util.Log.d(this.javaClass.canonicalName, "disconnect() called")
        scope.launch {
            // 1) отменяем текущий connect, если он ещё идёт
            connectJob?.cancelAndJoin()
            connectJob = null
            // 2) гасим VPN, если он поднялся или частично поднялся
            runCatching { engine.stop() }
        }
    }

    suspend fun close() {
        connectJob?.cancelAndJoin()
        runCatching { engine.stop() }
        scope.cancel()
    }
}