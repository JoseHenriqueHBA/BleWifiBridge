package com.example.blewifibridge

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var wsManager: WebSocketManager

    // estado observável pela UI
    private val logLines = mutableStateListOf<String>()
    private var bleStatus = mutableStateOf("Desconectado")
    private var wsStatus = mutableStateOf("Desconectado")

    private fun appendLog(msg: String) {
        runOnUiThread {
            logLines.add(msg)
            if (logLines.size > 200) logLines.removeAt(0)
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            bleManager.startScan()
        } else {
            appendLog("Permissões negadas. Não é possível escanear BLE.")
        }
    }

    private fun permissionsNeeded(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wsManager = WebSocketManager(object : WsListener {
            override fun onLog(message: String) = appendLog(message)
            override fun onOpen() { runOnUiThread { wsStatus.value = "Conectado" } }
            override fun onClosed() { runOnUiThread { wsStatus.value = "Desconectado" } }
        })

        bleManager = BleManager(this, object : BleListener {
            override fun onLog(message: String) = appendLog(message)
            override fun onConnected(deviceName: String?) {
                runOnUiThread { bleStatus.value = "Conectado (${deviceName ?: "ESP32"})" }
            }
            override fun onDisconnected() {
                runOnUiThread { bleStatus.value = "Desconectado" }
            }
            override fun onDataReceived(text: String) {
                appendLog("ESP32 -> $text")
                // repassa imediatamente para o servidor
                val sent = wsManager.send(text)
                if (!sent) appendLog("(não enviado: WebSocket desconectado)")
            }
        })

        setContent {
            MaterialTheme {
                BridgeScreen(
                    bleStatus = bleStatus.value,
                    wsStatus = wsStatus.value,
                    logLines = logLines,
                    onScanClick = { requestPermissions.launch(permissionsNeeded()) },
                    onConnectServer = { ip, port -> wsManager.connect(ip, port) },
                    onDisconnectServer = { wsManager.disconnect() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
        wsManager.disconnect()
    }
}

@Composable
fun BridgeScreen(
    bleStatus: String,
    wsStatus: String,
    logLines: List<String>,
    onScanClick: () -> Unit,
    onConnectServer: (String, String) -> Unit,
    onDisconnectServer: () -> Unit
) {
    var ip by remember { mutableStateOf("192.168.0.10") }
    var port by remember { mutableStateOf("8765") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("BLE ↔ WiFi Bridge", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Text("Status BLE: $bleStatus")
        Text("Status Servidor: $wsStatus")
        Spacer(Modifier.height(16.dp))

        Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear / Conectar ao ESP32")
        }

        Spacer(Modifier.height(16.dp))
        Text("Servidor (PC)", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("IP do computador") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Porta") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row {
            Button(onClick = { onConnectServer(ip, port) }, modifier = Modifier.weight(1f)) {
                Text("Conectar")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDisconnectServer, modifier = Modifier.weight(1f)) {
                Text("Desconectar")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Log", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp)
        ) {
            items(logLines.reversed()) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
