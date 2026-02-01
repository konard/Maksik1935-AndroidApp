package com.veillink.vpn.common.model

/** Хранит состояние подключения в данный момент**/
sealed class ConnectionState {
    data object Connecting : ConnectionState()
    data object Up : ConnectionState()
    data object Down : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}