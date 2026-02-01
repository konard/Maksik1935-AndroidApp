package com.veillink.vpn.android.singbox

import android.util.Log
import com.veillink.vpn.common.singbox.SingBoxBridge
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions

/**
 * Единственное место, где мы напрямую трогаем libbox API.
 * Если API поменяется — правишь только тут.
 */
class LibboxBridge : SingBoxBridge {

    private var service: BoxService? = null
    private var setupDone = false
    private var commandClient: CommandClient? = null

    @Synchronized
    override fun setup(basePath: String, tempPath: String, uid: Int) {
        if (setupDone) return
        val opt = SetupOptions().apply {
            setBasePath(basePath)
            setTempPath(tempPath)
        }
        Libbox.setup(opt)
        setupDone = true
    }

    override fun checkConfig(configJson: String) {
        Libbox.checkConfig(configJson)
    }

    @Synchronized
    override fun startService(
        configJson: String,
        platformInterface: Any,
        onLog: (String) -> Unit
    ) {
        if (service != null) return
        val pi = platformInterface as PlatformInterface
        val s = Libbox.newService(configJson, pi)
        s.start()
        // CommandClient нужен для URLTest/SelectOutbound/ServiceReload и т.п.
        commandClient = runCatching {
            Libbox.newStandaloneCommandClient().also { cc ->
                runCatching { cc.connect() }
                    .onFailure { Log.w("LibboxBridge", "CommandClient connect() failed", it) }
            }
        }.getOrNull()
        service = s
        onLog("libbox started")
    }

    @Synchronized
    override fun stopService() {
        service?.close()
        service = null
        runCatching { commandClient?.disconnect() }
        commandClient = null
    }

    override fun resetNetwork(): Boolean {
        val s = service ?: return false
        s.resetNetwork()
        return true
    }

    override fun urlTest(groupTag: String) {
        val cc = commandClient ?: return
        runCatching { cc.urlTest(groupTag) }
            .onFailure { Log.w("LibboxBridge", "urlTest($groupTag) failed", it) }
    }
}