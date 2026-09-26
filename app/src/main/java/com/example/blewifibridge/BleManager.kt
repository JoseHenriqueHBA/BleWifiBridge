package com.example.blewifibridge

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.*

/**
 * UUIDs do "Nordic UART Service" (NUS) - um padrão de fato para BLE UART.
 * Use os MESMOS UUIDs no firmware do ESP32 para que este app o reconheça
 * automaticamente, sem precisar mudar código do app.
 *
 * RX_CHAR = característica em que o CENTRAL (celular) ESCREVE -> ESP32 recebe
 * TX_CHAR = característica em que o PERIPHERAL (ESP32) NOTIFICA -> celular recebe
 */
object NusUuids {
    val SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val RX_CHAR: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val TX_CHAR: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}

interface BleListener {
    fun onLog(message: String)
    fun onConnected(deviceName: String?)
    fun onDisconnected()
    fun onDataReceived(text: String)
}

@SuppressLint("MissingPermission") // permissões são checadas na Activity antes de chamar
class BleManager(private val context: Context, private val listener: BleListener) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanning = false

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /** Escaneia por qualquer dispositivo BLE anunciando o serviço NUS. */
    fun startScan() {
        if (scanning) return
        scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            listener.onLog("Bluetooth não disponível ou desligado.")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(NusUuids.SERVICE))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true
        listener.onLog("Escaneando por ESP32...")
        scanner?.startScan(filters, settings, scanCallback)

        // timeout de segurança de 15s
        mainHandler.postDelayed({ if (scanning) stopScan() }, 15000)
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        scanner?.stopScan(scanCallback)
        listener.onLog("Scan finalizado.")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            val device = result.device
            listener.onLog("Dispositivo encontrado: ${device.name ?: device.address}")
            connect(device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onLog("Falha no scan (código $errorCode)")
        }
    }

    private fun connect(device: BluetoothDevice) {
        listener.onLog("Conectando a ${device.address}...")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener.onLog("Conectado. Descobrindo serviços...")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    listener.onLog("Desconectado do ESP32.")
                    listener.onDisconnected()
                    g.close()
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onLog("Erro ao descobrir serviços: $status")
                return
            }
            val service = g.getService(NusUuids.SERVICE)
            if (service == null) {
                listener.onLog("Serviço NUS não encontrado neste dispositivo.")
                return
            }
            val txChar = service.getCharacteristic(NusUuids.TX_CHAR)
            if (txChar != null) {
                g.setCharacteristicNotification(txChar, true)
                val cccd = txChar.getDescriptor(NusUuids.CCCD)
                if (cccd != null) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }
            listener.onConnected(g.device.name)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == NusUuids.TX_CHAR) {
                val text = characteristic.value?.toString(Charsets.UTF_8) ?: return
                listener.onDataReceived(text)
            }
        }
    }

    /** Opcional: envia comando de volta para o ESP32 pela característica RX. */
    fun sendCommand(text: String) {
        val g = gatt ?: return
        val service = g.getService(NusUuids.SERVICE) ?: return
        val rxChar = service.getCharacteristic(NusUuids.RX_CHAR) ?: return
        rxChar.value = text.toByteArray(Charsets.UTF_8)
        g.writeCharacteristic(rxChar)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
