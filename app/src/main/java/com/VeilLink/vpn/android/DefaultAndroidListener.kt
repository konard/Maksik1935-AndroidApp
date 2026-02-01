package com.veillink.vpn.android


import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Минимальный трекер UNDERLAY сети для VPN.
 *
 * Выбор сети отдаём системе (best matching), без перебора allNetworks/скоринга.
 *
 * Поведение по API (minSdk = 29):
 *  - API 31+: registerBestMatchingNetworkCallback(request)
 *  - API 29–30: registerNetworkCallback(request) (пассивный слушатель, экономит батарею)
 */
object DefaultNetworkListener {

    data class UnderlayState(
        val network: Network?,
        val eligible: Boolean,
        val validated: Boolean,
        val iface: String?,          // LinkProperties.interfaceName (опционально)
        val linkFingerprint: Int     // дешёвый отпечаток DNS/routes/IP
    )

    private const val TAG = "DefaultNetworkListener"

    @Volatile private var cm: ConnectivityManager? = null

    private val _state = MutableStateFlow(
        UnderlayState(network = null, eligible = false, validated = false, iface = null, linkFingerprint = 0)
    )
    val state: StateFlow<UnderlayState> = _state.asStateFlow()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @Volatile private var started = false

    // Текущая сеть, которую система считает “best matching” под наш request
    @Volatile private var current: Network? = null

    private val request: NetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    fun init(context: Context) {
        if (cm == null) {
            cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        }
    }

    @Synchronized
    fun start() {
        if (started) return

        val cm = this.cm ?: run {
            publish(null, "no-cm")
            return
        }

        thread = HandlerThread("vpn-underlay-tracker", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        handler = Handler(thread!!.looper)

        // Важно: колбэк может вызваться сразу после регистрации.
        // Поэтому считаем listener запущенным ДО register*(), чтобы не потерять первое событие.
        started = true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                cm.registerBestMatchingNetworkCallback(request, callback, handler!!)
            } else {
                // API 29–30: registerNetworkCallback — пассивный слушатель,
                // не создаёт "запрос" на сеть (в отличие от requestNetwork),
                // экономит батарею. VPN-сеть фильтруется в publish().
                cm.registerNetworkCallback(request, callback, handler!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "register callback failed", e)
            runCatching { thread?.quitSafely() }
            thread = null
            handler = null
            started = false
            publish(null, "register-failed")
            return
        }

        // Быстрое начальное состояние (если система уже знает сеть)
        runCatching { cm.activeNetwork }
            .getOrNull()
            ?.let { n ->
                // activeNetwork на старте часто является underlay; publish() сам отфильтрует VPN.
                publish(n, "start-active")
            }
            ?: publish(null, "start")
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false

        runCatching { cm?.unregisterNetworkCallback(callback) }
        runCatching { handler?.removeCallbacksAndMessages(null) }

        current = null
        publish(null, "stop")

        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            current = network
            publish(network, "onAvailable")
        }

        override fun onLost(network: Network) {
            if (current == network) {
                current = null
                publish(null, "onLost")
            }
        }

        override fun onUnavailable() {
            current = null
            publish(null, "onUnavailable")
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (current == null || current == network) {
                current = network
                publish(network, "onCaps", caps = networkCapabilities)
            }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (current == null || current == network) {
                current = network
                publish(network, "onLinkProps", lp = linkProperties)
            }
        }
    }

    private fun publish(
        network: Network?,
        reason: String,
        caps: NetworkCapabilities? = null,
        lp: LinkProperties? = null,
    ) {
        // после stop() возможны "хвостовые" колбэки — игнорим
        if (!started && reason != "stop" && reason != "no-cm" && reason != "register-failed") return

        val cm = cm
        if (cm == null || network == null) {
            val newState = UnderlayState(null, false, false, null, 0)
            val old = _state.value
            if (newState != old) {
                _state.value = newState
                Log.d(TAG, "underlay state changed: $old -> $newState ($reason)")
            }
            return
        }

        val realCaps = caps ?: cm.getNetworkCapabilities(network)
        if (realCaps == null) {
            publish(null, "$reason/caps-null")
            return
        }

        // Никогда не подкладываем VPN под VPN (loop). Если система вдруг вернула VPN — считаем что сети нет.
        if (realCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            publish(null, "$reason/vpn")
            return
        }

        val realLp = lp ?: cm.getLinkProperties(network)

        val eligible = realCaps.isEligibleForVpn()
        val validated = realCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val iface = realLp?.interfaceName
        val fp = realLp?.let { linkFingerprint(it) } ?: 0

        val newState = UnderlayState(network, eligible, validated, iface, fp)
        val old = _state.value
        if (newState != old) {
            _state.value = newState
            Log.d(TAG, "underlay state changed: $old -> $newState ($reason)")
        }
    }

    private fun NetworkCapabilities.isEligibleForVpn(): Boolean {
        if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
        if (!hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return false
        if (!hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) return false
        return true
    }

    // Дешёвый отпечаток linkprops (без строк/сортировок)
    private fun linkFingerprint(lp: LinkProperties): Int {
        fun mixUnorderedInts(ints: Iterable<Int>): Int {
            var sum = 0
            var xor = 0
            for (v in ints) {
                sum += v
                xor = xor xor v
            }
            return 31 * sum + xor
        }

        val addrH = mixUnorderedInts(lp.linkAddresses.map { la ->
            31 * la.address.hashCode() + la.prefixLength
        })
        val dnsH = mixUnorderedInts(lp.dnsServers.map { it.hashCode() })
        val routeH = mixUnorderedInts(lp.routes.map { it.hashCode() })

        var h = 17
        h = 31 * h + (lp.interfaceName?.hashCode() ?: 0)
        h = 31 * h + addrH
        h = 31 * h + dnsH
        h = 31 * h + routeH
        return h
    }
}