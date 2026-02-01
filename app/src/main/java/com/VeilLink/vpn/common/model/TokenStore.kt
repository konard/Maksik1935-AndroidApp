package com.veillink.vpn.common.model

interface TokenStore {
    suspend fun getAccessToken(): String?
    suspend fun setAccessToken(token: String?)
    suspend fun clear()
}