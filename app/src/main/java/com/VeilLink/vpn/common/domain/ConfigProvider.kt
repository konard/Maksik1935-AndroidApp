package com.veillink.vpn.common.domain

/** Поставщик готового конфига (строка Amnezia‑WG) */
interface ConfigProvider {
    suspend fun fetchConfigText(): String
}