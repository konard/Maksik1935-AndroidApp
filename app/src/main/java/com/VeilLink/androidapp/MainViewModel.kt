package com.VeilLink.androidapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.VeilLink.androidapp.network.VpnApiService
import kotlinx.coroutines.launch

class MainViewModel(private val api: VpnApiService = VpnApiService()) : ViewModel() {
    var login by mutableStateOf("")
    var password by mutableStateOf("")
    var servers by mutableStateOf<List<String>>(emptyList())
    var selectedServer by mutableStateOf<String?>(null)
    var config by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var loggedIn by mutableStateOf(false)

    fun performLogin() {
        viewModelScope.launch {
            val success = api.login(login, password)
            if (success) {
                loggedIn = true
                servers = api.getServers()
                if (servers.isNotEmpty()) {
                    selectedServer = servers.first()
                }
            } else {
                error = "Login failed"
            }
        }
    }

    fun loadConfig() {
        val server = selectedServer ?: return
        viewModelScope.launch {
            config = api.getConfig(server)
        }
    }

    fun connect() {
        // TODO: Use Amnezia library to establish VPN connection with config
    }
}
