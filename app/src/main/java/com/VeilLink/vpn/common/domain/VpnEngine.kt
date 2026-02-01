package com.veillink.vpn.common.domain

import com.veillink.vpn.common.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow


/** Платформенный движок VPN: умеет стартовать/останавливать по готовой текстовой строке конфига */
interface VpnEngine {
    val state: StateFlow<ConnectionState>
    fun start(configText: String)
    fun stop()
}