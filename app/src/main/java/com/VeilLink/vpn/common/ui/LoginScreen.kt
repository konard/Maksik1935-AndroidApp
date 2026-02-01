package com.veillink.vpn.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorText: String?,
    onLoginClick: (String, String) -> Unit,
) {
    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF00122A),
            Color(0xFF020617)
        )
    )

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xCC111827)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LogoHeader()

                Text(
                    text = "Вход",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                DarkTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = "Логин / e-mail",
                    singleLine = true
                )

                DarkTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Пароль",
                    singleLine = true,
                    isPassword = true
                )

                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = { onLoginClick(login, password) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        disabledContainerColor = Color(0xFF4B5563),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isLoading) "Входим…" else "Войти",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    isPassword: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    val visualTransformation =
        if (!isPassword || passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        label = {
            Text(text = label, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        },
        visualTransformation = visualTransformation,
        trailingIcon = {
            if (isPassword) {
                val iconText = if (passwordVisible) "🙈" else "👁️"
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(iconText, fontSize = 14.sp)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0x661F2937),
            unfocusedContainerColor = Color(0x661F2937),
            disabledContainerColor = Color(0x661F2937),
            focusedBorderColor = Color(0xFF2563EB),
            unfocusedBorderColor = Color(0x334B5563),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF60A5FA)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}