package com.veillink.vpn.common.domain

interface Logger {
    fun d(msg: String)
    fun e(msg: String, t: Throwable? = null)
}