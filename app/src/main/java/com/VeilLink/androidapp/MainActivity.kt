package com.VeilLink.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.VeilLink.androidapp.ui.theme.AndroidAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidAppTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    if (!viewModel.loggedIn) {
        LoginScreen(viewModel)
    } else {
        ServerScreen(viewModel)
    }
}

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = viewModel.login,
            onValueChange = { viewModel.login = it },
            label = { Text("Login") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.performLogin() }) {
            Text("Login")
        }
        if (viewModel.error != null) {
            Text(
                viewModel.error!!,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ServerScreen(viewModel: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select server")
        for (server in viewModel.servers) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.selectedServer == server,
                    onClick = { viewModel.selectedServer = server }
                )
                Text(server)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.loadConfig() }) {
            Text("Get Config")
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (viewModel.config.isNotBlank()) {
            Button(onClick = { viewModel.connect() }) {
                Text("Connect")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AndroidAppTheme {
        MainScreen(MainViewModel())
    }
}