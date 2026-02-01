package com.veillink.vpn.common.singbox

/**
 * Мост к конкретной реализации ядра.
 */
interface SingBoxBridge {
    fun setup(basePath: String, tempPath: String, uid: Int)

    fun checkConfig(configJson: String)

    fun startService(
        configJson: String,
        platformInterface: Any,
        onLog: (String) -> Unit
    )

    fun stopService()

    /**
     * Попросить ядро “сбросить сеть” (в терминах sing-box: Router.ResetNetwork()).
     *
     * ⚠️ Это НЕ “мягкий conntrack.Close() как в NekoBox”, а стандартный публичный API libbox:
     * BoxService.ResetNetwork(). Если реализация не поддерживает — вернуть false.
     */
    fun resetNetwork(): Boolean = false

    fun urlTest(groupTag: String)

    /**
     * Переключить выбранный outbound в группе.
     * @return true если переключение удалось
     */
    fun selectOutbound(groupTag: String, outboundTag: String): Boolean = false
}