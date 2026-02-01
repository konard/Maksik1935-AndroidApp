package com.veillink.vpn.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veillink.vpn.android.AndroidConfigProvider
import com.veillink.vpn.android.AndroidTokenStore
import com.veillink.vpn.android.AndroidVpnEngine
import com.veillink.vpn.common.control.VpnController
import com.veillink.vpn.common.model.ApiException
import com.veillink.vpn.common.model.AuthState
import com.veillink.vpn.common.model.ConnectionState
import com.veillink.vpn.common.network.ApiService
import com.veillink.vpn.common.network.HttpClientFactory
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ViewModel — удобный «долгожитель» между экраном и бизнес-логикой.
 * Здесь создаются движок, сеть и контроллер. UI видит только state и методы connect/disconnect.
 */
class VpnControllerViewModel(app: Application) : AndroidViewModel(app) {

    // Хранилище токена на Android
    private val tokenStore = AndroidTokenStore(app.applicationContext)

    // --- состояние авторизации ---
    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> get() = _authState

    // Ktor HttpClient c JWT + auto-renew
    private val httpClient = HttpClientFactory(
        baseUrl = "http://45.144.233.180:8081",
        tokenStore = tokenStore,
        engineFactory = CIO,
        onAuthLost = {
            // При 401 + провале renew → считаем, что авторизация потеряна
            _authState.value = AuthState.Unauthed
        }
    ).create()

    // Общий API-сервис
    val apiService = ApiService(httpClient)

    // VPN-движок и контроллер
    private val controller: VpnController by lazy {
        VpnController(
            engine = AndroidVpnEngine(app.applicationContext),
            // пока используем локальный AndroidConfigProvider,
            // позже можно сделать ConfigProvider на базе apiService
            configs = AndroidConfigProvider()
        )
    }
    // состояние сервиса (через engine → controller)
    val state: StateFlow<ConnectionState> get() = controller.state

    init {
        // при старте проверяем, есть ли сохранённый токен
        viewModelScope.launch {
            val token = tokenStore.getAccessToken()
            _authState.value = if (token.isNullOrEmpty()) {
                AuthState.Unauthed
            } else {
                AuthState.Authed
            }
        }
    }

    fun connect() = controller.connect()
    fun disconnect() = controller.disconnect()

    fun login(
        email: String,
        password: String,
        onResult: (success: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = apiService.login(email, password)
                tokenStore.setAccessToken(token)
                _authState.value = AuthState.Authed
                onResult(true)
            } catch (e: ApiException) {
                showError(e.apiError.message)
                onResult(false)
            } catch (e: Exception) {
                showError("Что-то пошло не так. Попробуйте позже.")
                // на всякий случай fallback
                onResult(false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            _authState.value = AuthState.Unauthed
        }
    }

    //Окно с ошибкой при запросе
    sealed interface UiEvent {
        data class Error(val message: String) : UiEvent
    }

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private fun showError(message: String) {
        _uiEvents.tryEmit(UiEvent.Error(message))
    }

    override fun onCleared() {
        super.onCleared()
    }
}