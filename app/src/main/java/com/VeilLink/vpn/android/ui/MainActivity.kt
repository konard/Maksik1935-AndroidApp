package com.veillink.vpn.android.ui

import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import com.veillink.vpn.common.ui.RootScreen

class MainActivity : ComponentActivity() {

    private val vm: VpnControllerViewModel by viewModels()

    private val prepare = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == RESULT_OK) vm.connect() else
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RootScreen(
                vm = vm,
                requestVpnPermission = { requestVpnPermission() }
            )
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) prepare.launch(intent) else vm.connect()
    }
}