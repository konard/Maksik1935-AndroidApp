package com.veillink.vpn.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veillink.vpn.common.model.ConnectionState

@Composable
fun MainScreen(
    state: ConnectionState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    // Чтобы потом легко перенести в shared — никакого Android-специфичного кода.
    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF00122A), // тёмный верх, как hero-bg
            Color(0xFF020617)  // слегка темнее книзу
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(36.dp),
        contentAlignment = Alignment.Center
    ) {
        // Кнопка "Выйти" в правом верхнем углу
        TextButton(
            onClick = onLogoutClick,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text(
                text = "Выйти",
                color = Color(0xFFE5E7EB)
            )
        }
        // Центральная карточка приложения
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xCC111827) // полупрозрачный тёмный, как твои панели
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. "Логотип" + название (примерно 10% высоты карточки)
                LogoHeader()

                // 2. Блок выбранного сервера
                ServerBlockPlaceholder()

                // 3. Кнопка коннекта + статус
                ConnectionControls(
                    state = state,
                    onConnectClick = onConnectClick,
                    onDisconnectClick = onDisconnectClick
                )
            }
        }
    }
}
@Composable
fun LogoHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Круглый "значок"
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = Color(0xFF0EA5E9), // голубой акцент, как кнопки на вебе
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "V",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "VeilLink",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Secure VPN",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )
        }
    }
}
@Composable
private fun ServerBlockPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x661F2937), // прозрачный графит
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Text(
            text = "Выбранный сервер",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Frankfurt-01",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Заглушка — позже подтянем реальные данные",
                    color = Color(0xFF6B7280),
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }

            // маленький "чип" с пингом-заглушкой
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0x33256EB6),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Ping ~ 45 ms",
                    color = Color(0xFF60A5FA),
                    fontSize = 11.sp
                )
            }
        }
    }
}
@Composable
private fun ConnectionControls(
    state: ConnectionState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val (buttonText, buttonEnabled, isPrimaryAction) = when (state) {
        is ConnectionState.Up -> Triple("Отключиться", true, false)
        is ConnectionState.Connecting -> Triple("Подключение…", false, true)
        is ConnectionState.Down -> Triple("Подключиться", true, true)
        is ConnectionState.Error -> Triple("Повторить подключение", true, true)
    }

    // Кнопка
    Button(
        onClick = {
            when (state) {
                is ConnectionState.Up -> onDisconnectClick()
                else -> onConnectClick()
            }
        },
        enabled = buttonEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimaryAction) Color(0xFF2563EB) else Color(0xFFDC2626),
            disabledContainerColor = Color(0xFF4B5563),
            contentColor = Color.White
        )
    ) {
        Text(
            text = buttonText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    // Строка статуса
    Spacer(modifier = Modifier.height(8.dp))

    val (statusText, statusColor) = when (state) {
        is ConnectionState.Up -> "Подключено" to Color(0xFF22C55E)
        is ConnectionState.Connecting -> "Подключение…" to Color(0xFFFACC15)
        is ConnectionState.Down -> "Отключено" to Color(0xFF9CA3AF)
        is ConnectionState.Error -> "Ошибка подключения" to Color(0xFFEF4444)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText,
            color = Color(0xFFE5E7EB),
            fontSize = 13.sp
        )
    }
}