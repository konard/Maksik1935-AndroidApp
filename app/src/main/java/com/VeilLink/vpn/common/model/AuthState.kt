package com.veillink.vpn.common.model

sealed class AuthState {
    data object Checking : AuthState()   // ещё не знаем, есть токен или нет. Удобно, чтобы не мигать экраном, пока не поняли где мы
    data object Authed   : AuthState()   // авторизован
    data object Unauthed : AuthState()   // не авторизован
}