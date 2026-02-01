package com.veillink.vpn.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.veillink.vpn.android.singbox.AndroidPlatformInterface
import com.veillink.vpn.android.singbox.LibboxBridge
import com.veillink.vpn.android.ui.MainActivity
import com.veillink.vpn.common.model.ConnectionState
import com.veillink.vpn.common.singbox.SingBoxCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

class SingBoxService : VpnService() {

    companion object {
        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Down)
        val state: StateFlow<ConnectionState> = _state

        const val CONFIG = "config"
        const val ACTION_CONNECT = "CONNECT"
        const val ACTION_DISCONNECT = "DISCONNECT"

        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "vpn_service_channel"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var core: SingBoxCore? = null
    private var platform: AndroidPlatformInterface? = null
    private val bridge = LibboxBridge()

    private val cm by lazy { getSystemService(ConnectivityManager::class.java) }

    // Текущая выбранная underlay-сеть (потокобезопасно — ядро может читать не из dispatcher'а)
    private val currentNetworkRef = AtomicReference<Network?>(null)

    // Мы обновляем setUnderlyingNetworks только когда Network реально поменялся (или сбрасываем на null)
    private var underlyingApplied: Network? = null

    private var networkCollectJob: Job? = null

    // pending underlay до того, как VPN реально поднят (Up)
    private var pendingUnderlying: Network? = null

    // 1 ретрай применения setUnderlyingNetworks, если вдруг упало
    private var underlyingRetryJob: Job? = null
    private var underlyingRetryFor: Network? = null
    private val UNDERLYING_RETRY_DELAY_MS = 1_500L

    // resetNetwork троттлинг
    private var lastResetAtMs: Long = 0
    private val RESET_MIN_INTERVAL_MS = 15_000L
    private val RESET_NETCHANGE_MIN_INTERVAL_MS = 1_000L

    // --- urltest orchestration ---
    private var urlTestJob: Job? = null
    private var lastUrlTestAtMs: Long = 0L

    private val URLTEST_GROUP_TAG = "auto"
    private val URLTEST_DEBOUNCE_MS = 800L
    private val URLTEST_MIN_INTERVAL_MS = 8_000L

    private var prevUnderlay: DefaultNetworkListener.UnderlayState? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(javaClass.simpleName, "onStartCommand action=${intent?.action}, startId=$startId")

        return when (intent?.action) {
            ACTION_CONNECT -> {
                val cfgText = intent.getStringExtra(CONFIG)
                Log.d(javaClass.simpleName, "ACTION_CONNECT, config length=${cfgText?.length ?: 0}")

                if (cfgText.isNullOrBlank()) {
                    stopSelf()
                    START_NOT_STICKY
                } else if (_state.value is ConnectionState.Connecting || _state.value is ConnectionState.Up) {
                    START_NOT_STICKY
                } else {
                    startForegroundCompat(buildNotification("Connecting…"))
                    scope.launch { startVpn(cfgText) }
                    START_REDELIVER_INTENT
                }
            }

            ACTION_DISCONNECT -> {
                startForegroundCompat(buildNotification("Disconnecting…"))
                Log.d(javaClass.simpleName, "ACTION_DISCONNECT")
                scope.launch {
                    stopVpn()
                    stopSelf()
                }
                START_NOT_STICKY
            }

            else -> {
                Log.w(javaClass.simpleName, "Unknown action=${intent?.action}")
                stopSelf()
                START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        runBlocking { stopVpn() }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startVpn(configJson: String) {
        try {
            Log.d(javaClass.simpleName, "startVpn(): entered, config length=${configJson.length}")
            _state.value = ConnectionState.Connecting
            updateNotif("Connecting…")

            // 1) стартуем underlay listener
            DefaultNetworkListener.init(this)
            DefaultNetworkListener.start()

            // 2) начинаем собирать UnderlayState
            networkCollectJob?.cancel()
            networkCollectJob = scope.launch {
                DefaultNetworkListener.state.collect { cur ->
                    handleUnderlayState(cur)
                }
            }


            platform = AndroidPlatformInterface(
                vpnService = this,
                underlyingNetwork = { currentNetworkRef.get() },
                onLog = { msg -> Log.d("core", msg) }
            )

            core = SingBoxCore(
                bridge = bridge,
                basePath = filesDir.absolutePath,
                tempPath = cacheDir.absolutePath,
                uid = android.os.Process.myUid(),
                platformInterface = platform!!,
                onLog = { msg -> Log.d("SingBoxCore", msg) }
            )

            core!!.start(configJson)

            _state.value = ConnectionState.Up
            // Теперь VPN реально поднят — можно безопасно применить pending underlay
            applyUnderlyingNetwork(pendingUnderlying ?: currentNetworkRef.get())
            pendingUnderlying = null
            updateNotif("Соединение защищено")
            Log.d(javaClass.simpleName, "VPN state set to Up")
        } catch (t: Throwable) {
            Log.e(javaClass.simpleName, "startVpn failed", t)
            _state.value = ConnectionState.Error(t.message ?: "sing-box start failed")
            updateNotif("Ошибка подключения")
            stopVpn()
        }
    }

    private suspend fun stopVpn() {
        Log.d(javaClass.simpleName, "stopVpn() called")

        networkCollectJob?.cancel()
        networkCollectJob = null
        prevUnderlay = null

        runCatching { DefaultNetworkListener.stop() }

        currentNetworkRef.set(null)
        underlyingApplied = null

        lastResetAtMs = 0

        urlTestJob?.cancel()
        urlTestJob = null
        lastUrlTestAtMs = 0
        underlyingRetryJob?.cancel()
        underlyingRetryJob = null
        underlyingRetryFor = null
        pendingUnderlying = null

        try {
            core?.stop()
            platform?.closeTun()
        } catch (t: Throwable) {
            Log.w(javaClass.simpleName, "stopVpn error", t)
        } finally {
            core = null
            platform = null
            _state.value = ConnectionState.Down
            stopForegroundCompat()
            Log.d(javaClass.simpleName, "VPN state set to Down")
        }
    }

    private fun handleUnderlayState(cur: DefaultNetworkListener.UnderlayState) {
        val prev = prevUnderlay
        prevUnderlay = cur

        // 0) ref для ядра (PlatformInterface читает это)
        currentNetworkRef.set(cur.network)

        // 1) Underlay применяем только когда VPN уже Up.
        //    Пока Connecting — просто запоминаем pending.
        if (_state.value is ConnectionState.Up) {
            applyUnderlyingNetwork(cur.network)
        } else {
            pendingUnderlying = cur.network
        }

        // 2) UI: обновляем только по переходам eligible/validated
        if (_state.value is ConnectionState.Up &&
            (prev?.eligible != cur.eligible || prev?.validated != cur.validated)
        ) {
            when {
                !cur.eligible -> updateNotif("Нет сети / нет интернета")
                cur.eligible && !cur.validated -> updateNotif("Сеть есть, интернет не подтверждён")
                else -> updateNotif("Интернет подтверждён, проверяю сервер…")
            }
        }

        // 3) resetNetwork: на смену сети и на смену linkFingerprint (с троттлингом)
        maybeResetNetwork(prev, cur)

        // 4) urlTest: триггеры на "сеть появилась", "сеть сменилась", "validated стало true"
        maybeScheduleUrlTest(prev, cur)
    }

    private fun applyUnderlyingNetwork(net: Network?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        if (_state.value !is ConnectionState.Up) return

        if (underlyingApplied == net) return

        val ok = runCatching {
            if (net != null) setUnderlyingNetworks(arrayOf(net))
            else setUnderlyingNetworks(null)
        }.isSuccess

        if (ok) {
            underlyingApplied = net
            underlyingRetryFor = null
            underlyingRetryJob?.cancel()
            underlyingRetryJob = null
            return
        }

        // Ретрай делаем один раз на конкретную сеть (и только если она не изменилась)
        if (underlyingRetryFor == net) return
        underlyingRetryFor = net

        underlyingRetryJob?.cancel()
        underlyingRetryJob = scope.launch {
            delay(UNDERLYING_RETRY_DELAY_MS)
            if (_state.value !is ConnectionState.Up) return@launch
            if (currentNetworkRef.get() != net) return@launch
            if (underlyingApplied == net) return@launch

            runCatching {
                if (net != null) setUnderlyingNetworks(arrayOf(net))
                else setUnderlyingNetworks(null)
            }.onSuccess {
                underlyingApplied = net
            }.onFailure {
                // Дальше не крутимся в цикле — следующий ретрай будет только если сеть снова изменится
                underlyingRetryFor = null
            }
        }
    }

    private fun maybeResetNetwork(prev: DefaultNetworkListener.UnderlayState?, cur: DefaultNetworkListener.UnderlayState) {
        if (_state.value !is ConnectionState.Up) return
        if (cur.network == null) return

        val netChanged = prev?.network != cur.network
        val linkChanged =
            prev != null &&
                    prev.linkFingerprint != 0 &&
                    cur.linkFingerprint != 0 &&
                    prev.linkFingerprint != cur.linkFingerprint

        if (!netChanged && !linkChanged) return

        val now = SystemClock.elapsedRealtime()
        val minInterval = if (netChanged) RESET_NETCHANGE_MIN_INTERVAL_MS else RESET_MIN_INTERVAL_MS
        if (now - lastResetAtMs < minInterval) return

        runCatching { core?.resetNetwork() }
        lastResetAtMs = now
    }


    private fun maybeScheduleUrlTest(prev: DefaultNetworkListener.UnderlayState?, cur: DefaultNetworkListener.UnderlayState) {
        if (_state.value !is ConnectionState.Up) return
        if (!cur.eligible || !cur.validated) return
        if (cur.network == null) return

        val netChanged = prev?.network != cur.network
        val validatedBecameTrue = prev?.validated == false && cur.validated
        val eligibleBecameTrue = prev?.eligible == false && cur.eligible

        if (netChanged || validatedBecameTrue || eligibleBecameTrue) {
            val reason = when {
                validatedBecameTrue -> "validated false->true"
                eligibleBecameTrue -> "eligible false->true"
                else -> "network changed/restored"
            }
            scheduleUrlTest(reason, expectedNetwork = cur.network)
        }
    }


    private fun scheduleUrlTest(reason: String, expectedNetwork: Network) {
        urlTestJob?.cancel()
        urlTestJob = scope.launch {
            delay(URLTEST_DEBOUNCE_MS)

            if (_state.value !is ConnectionState.Up) return@launch
            if (currentNetworkRef.get() != expectedNetwork) return@launch

            // актуальное состояние берём из prevUnderlay (оно обновляется коллектором)
            val cur = prevUnderlay ?: return@launch
            if (!cur.eligible || !cur.validated) return@launch

            val now = SystemClock.elapsedRealtime()
            if (now - lastUrlTestAtMs < URLTEST_MIN_INTERVAL_MS) return@launch

            Log.d(javaClass.simpleName, "URLTest ($reason), group=$URLTEST_GROUP_TAG")
            lastUrlTestAtMs = now

            runCatching { core?.urlTest(URLTEST_GROUP_TAG) }
                .onFailure { Log.w(javaClass.simpleName, "urlTest failed", it) }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        // Тип берём из manifest, чтобы совпадал с android:foregroundServiceType
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        )
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}