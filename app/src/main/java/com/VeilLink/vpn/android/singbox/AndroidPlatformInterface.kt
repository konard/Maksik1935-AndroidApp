package com.veillink.vpn.android.singbox

import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.libbox.*

/**
 * Android-реализация libbox.PlatformInterface.
 * Делает только то, что нужно MVP: TUN + лог.
 */
/**
 * PlatformInterface для libbox/sing-box на Android.
 *
 * Почему почти пустой:
 * - sing-box сам реализует большую часть сетевой логики (DNS, маршрутизация, прокси-протоколы, dialer, etc.).
 * - На Android приложению нужно только:
 *   1) создать TUN через VpnService.Builder (openTun) и отдать fd ядру,
 *   2) подкладывать используемый интерфейс ядру (мы это делаем в SingBoxService)
 *   3) (опционально) логировать сообщения.
 *
 * Почему это безопасно:
 * - Трафик самого приложения исключён из VPN через Builder.addDisallowedApplication(packageName),
 *   поэтому ядро (sing-box) устанавливает исходящие соединения напрямую в сеть и не попадает в VPN-loop.
 * - Underlying network прокидывается в Builder.setUnderlyingNetworks(...) (и далее через VpnService.setUnderlyingNetworks),
 *   чтобы Android корректно маршрутизировал трафик VPN поверх текущей активной сети (Wi-Fi/LTE) при её смене.
 *
 * Остальные platform-хуки (monitor interface, pcap, procfs, etc.) не используются в этом приложении и поэтому выключены.
 */
class AndroidPlatformInterface(
    private val vpnService: VpnService,
    private val underlyingNetwork: () -> Network? = { null },
    private val onLog: (String) -> Unit = {}
) : PlatformInterface {

    private var tunPfd: ParcelFileDescriptor? = null

    fun closeTun() {
        runCatching { tunPfd?.close() }
        tunPfd = null
    }

    override fun openTun(options: TunOptions): Int {
        closeTun()

        val builder = vpnService.Builder()
            .setSession("VeilLink")
            .setMtu(options.mtu)

        //Убираем петлю
        runCatching { builder.addDisallowedApplication(vpnService.packageName) }
            .onFailure { Log.w("AndroidPlatformInterface", "addDisallowedApplication failed", it) }

        // IPv4 адреса из options
        val inet4 = options.inet4Address
        while (inet4.hasNext()) {
            val p = inet4.next()
            builder.addAddress(p.address(), p.prefix())
        }

        // IPv6 адреса из options
        val inet6 = options.inet6Address
        while (inet6.hasNext()) {
            val p = inet6.next()
            builder.addAddress(p.address(), p.prefix())
        }

        // Маршруты
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)

        // DNS: задаём "внутренний" DNS в подсети tun, чтобы приложения шли в VPN за резолвом
        builder.addDnsServer("172.19.0.2")

        // Важно: указываем underlying network, чтобы Android не пытался отправлять трафик VPN в сам VPN.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            underlyingNetwork()?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        }

        val pfd = builder.establish() ?: error("VpnService.Builder.establish() returned null")
        tunPfd = pfd
        return pfd.fd
    }

    override fun writeLog(message: String) {
        onLog(message)
    }

    override fun usePlatformAutoDetectInterfaceControl() = false
    override fun autoDetectInterfaceControl(fd: Int) {
    }

    override fun clearDNSCache() {
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
    }

    // Reality/VLESS не нуждаются в этом для старта.
    override fun systemCertificates(): StringIterator? {
        return null
    }

    override fun getInterfaces(): NetworkInterfaceIterator? {
        return null
    }

    override fun includeAllNetworks(): Boolean {
        return true
    }

    //не используем локальный DNS-транспорт, DNS полностью из конфига sing-box.
    override fun localDNSTransport(): LocalDNSTransport? {
        return null
    }

    override fun underNetworkExtension() = false

    override fun useProcFS() = false
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int = 0

    override fun packageNameByUid(uid: Int): String = ""

    //Инфу о wifi можно не давать, ядро умеет жить без этого
    override fun readWIFIState(): WIFIState? {
        return null;
    }

    //Нотификации обрабатываются отдельно в другом месте
    override fun sendNotification(notification: Notification?) {
    }

    override fun uidByPackageName(packageName: String): Int = -1
}