package com.veillink.vpn.common.singbox

/**
 * Тонкий переносимый адаптер к ядру.
 * Состояния (ConnectionState) ведёт сервис, тут их нет.
 */
class SingBoxCore(
    private val bridge: SingBoxBridge,
    private val basePath: String,
    private val tempPath: String,
    private val uid: Int,
    private val platformInterface: Any,
    private val onLog: (String) -> Unit = {}
) {
    private var started = false

    @Synchronized
    fun start(configJson: String) {
        if (started) return
        bridge.setup(basePath, tempPath, uid)
        bridge.checkConfig(configJson)
        bridge.startService(configJson, platformInterface, onLog)
        started = true
    }

    @Synchronized
    fun stop() {
        if (!started) return
        bridge.stopService()
        started = false
    }

    @Synchronized
    fun resetNetwork(): Boolean {
        if (!started) return false
        return bridge.resetNetwork()
    }

    @Synchronized
    fun urlTest(groupTag: String) {
        if (!started) return
        bridge.urlTest(groupTag)
    }

    @Synchronized
    fun selectOutbound(groupTag: String, outboundTag: String): Boolean {
        if (!started) return false
        return bridge.selectOutbound(groupTag, outboundTag)
    }
}