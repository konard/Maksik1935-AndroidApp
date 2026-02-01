package com.veillink.vpn.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.veillink.vpn.android.ui.VpnControllerViewModel
import com.veillink.vpn.common.model.AuthState
import com.veillink.vpn.common.model.ConnectionState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RootScreen(
    vm: VpnControllerViewModel,
    requestVpnPermission: () -> Unit
) {
    val authState by vm.authState.collectAsState(initial = AuthState.Checking)
    val vpnState by vm.state.collectAsState(initial = ConnectionState.Down)
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    //Сбор сообщений об ошибках
    LaunchedEffect(vm) {
        vm.uiEvents.collectLatest { event ->
            when (event) {
                is VpnControllerViewModel.UiEvent.Error -> dialogMessage = event.message
            }
        }
    }

    when (authState) {
        is AuthState.Checking -> {
            // простой сплэш/лоадер
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF020617)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is AuthState.Unauthed -> {
            var isLoading by remember { mutableStateOf(false) }
            // инлайн-ошибка (только для формы)
            var inlineError by remember { mutableStateOf<String?>(null) }


            LoginScreen(
                isLoading = isLoading,
                errorText = inlineError,
                onLoginClick = { login, password ->
                    if (login.isBlank() || password.isBlank()) {
                        inlineError = "Введите логин и пароль"
                        return@LoginScreen
                    }

                    isLoading = true
                    inlineError = null

                    vm.login(login, password) { success ->
                        isLoading = false
                    }
                }
            )
        }

        is AuthState.Authed -> {
            MainScreen(
                state = vpnState,
                onConnectClick = { requestVpnPermission() },
                onDisconnectClick = { vm.disconnect() },
                onLogoutClick = { vm.logout() }
            )
        }
    }
    dialogMessage?.let { msg ->
        ErrorDialog(
            title = "Ошибка",
            message = msg,
            onDismiss = { dialogMessage = null }
        )
    }
}