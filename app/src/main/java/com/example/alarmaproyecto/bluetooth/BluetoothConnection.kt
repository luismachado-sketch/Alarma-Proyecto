package com.example.alarmaproyecto.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

data class PairedDeviceInfo(
    val name: String,
    val address: String,
    val type: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN
)

/**
 * Soporta DOS transportes:
 *  - Clasico SPP (RFCOMM) para HC-05.
 *  - BLE GATT (FFE0/FFE1) para modulos seriales tipo HM-10 / "HC-06 ALARMA".
 *
 * El transporte se decide por el tipo del dispositivo (device.type):
 *  LE -> BLE GATT ; CLASSIC/DUAL/UNKNOWN -> SPP clasico.
 */
object BluetoothConnection {

    private const val TAG = "BT_ALARMA"
    private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    private val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _discovered = MutableStateFlow<List<PairedDeviceInfo>>(emptyList())
    val discovered: StateFlow<List<PairedDeviceInfo>> = _discovered

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering

    // === Estado clasico ===
    private var socket: BluetoothSocket? = null
    private var receiver: BroadcastReceiver? = null
    private var pendingPairAddress: String? = null

    // === Estado BLE ===
    private var gatt: BluetoothGatt? = null
    private var charUart: BluetoothGattCharacteristic? = null
    private val buffer = StringBuilder()
    private val main = Handler(Looper.getMainLooper())

    fun clearError() {
        _error.value = null
    }

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun hasScanPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    private fun esModuloBluetooth(nombre: String): Boolean {
        val n = nombre.uppercase()
        return n.startsWith("HC-05") || n.startsWith("HC05") ||
            n.startsWith("HC-06") || n.startsWith("HC06") ||
            n.startsWith("HM-") || n.startsWith("HM10") ||
            n.contains("ALARMA") || n.contains("ARDUINO")
    }

    /** Lista dispositivos ya vinculados. */
    @SuppressLint("MissingPermission")
    fun bondedDevices(context: Context): List<PairedDeviceInfo> {
        val appContext = context.applicationContext
        if (!hasConnectPermission(appContext)) return emptyList()
        val btAdapter = adapter(appContext) ?: return emptyList()
        return try {
            (btAdapter.bondedDevices ?: emptySet())
                .map { PairedDeviceInfo(it.name ?: "Sin nombre", it.address, it.type) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun agregarDescubierto(info: PairedDeviceInfo) {
        if (_discovered.value.none { it.address == info.address }) {
            _discovered.value = _discovered.value + info
        }
    }

    // === ESCANEO (clasico + BLE) ===

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val nombre = try {
                result.scanRecord?.deviceName ?: device.name
            } catch (e: SecurityException) {
                null
            } ?: return
            if (!esModuloBluetooth(nombre)) return
            Log.d(TAG, "BLE encontrado: $nombre / ${device.address}")
            agregarDescubierto(PairedDeviceInfo(nombre, device.address, BluetoothDevice.DEVICE_TYPE_LE))
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(context: Context) {
        val appContext = context.applicationContext
        if (!hasScanPermission(appContext)) {
            _error.value = "Permiso de escaneo no concedido. Activa \"Dispositivos cercanos\"."
            return
        }
        val btAdapter = adapter(appContext)
        if (btAdapter == null || !btAdapter.isEnabled) {
            _error.value = "Bluetooth apagado o no disponible."
            return
        }

        registerReceiver(appContext)
        _discovered.value = emptyList()
        _discovering.value = true

        // Escaneo clasico (HC-05)
        try {
            btAdapter.cancelDiscovery()
            btAdapter.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "startDiscovery clasico fallo: ${e.message}")
        }

        // Escaneo BLE (HM-10 / HC-06 BLE)
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            btAdapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "startScan BLE fallo: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context) {
        val btAdapter = adapter(context.applicationContext) ?: return
        try {
            btAdapter.cancelDiscovery()
        } catch (_: Exception) {
        }
        try {
            btAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        _discovering.value = false
    }

    @SuppressLint("MissingPermission")
    private fun registerReceiver(context: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.parcelableDevice() ?: return
                        val nombre = try {
                            device.name
                        } catch (e: SecurityException) {
                            null
                        } ?: return
                        Log.d(TAG, "CLASSIC encontrado: $nombre / ${device.address} type=${device.type}")
                        agregarDescubierto(PairedDeviceInfo(nombre, device.address, device.type))
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Discovery clasico FINISHED. Encontrados=${_discovered.value.size}")
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = intent.parcelableDevice()
                        val estado = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE
                        )
                        Log.d(TAG, "BOND_STATE_CHANGED: mac=${device?.address} estado=$estado")
                        if (estado == BluetoothDevice.BOND_BONDED &&
                            device != null && device.address == pendingPairAddress
                        ) {
                            pendingPairAddress = null
                            connect(ctx.applicationContext, device.address)
                        }
                    }
                }
            }
        }
        receiver = r
        val filtro = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, filtro, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(r, filtro)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    // === CONEXION ===

    @SuppressLint("MissingPermission")
    fun connect(context: Context, address: String? = null) {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.CONNECTED) {
            return
        }
        val appContext = context.applicationContext
        if (!hasConnectPermission(appContext)) {
            _error.value = "Permiso Bluetooth no concedido."
            return
        }
        val btAdapter = adapter(appContext)
        if (btAdapter == null || !btAdapter.isEnabled) {
            _error.value = "Bluetooth apagado o no disponible."
            return
        }

        stopDiscovery(appContext)

        val objetivo = when {
            address != null -> _discovered.value.firstOrNull { it.address == address }
                ?: PairedDeviceInfo("Modulo", address)
            else -> _discovered.value.firstOrNull()
        }

        if (objetivo == null) {
            _error.value = "No hay modulo seleccionado. Usa \"Buscar modulo Bluetooth\" en el Dashboard."
            return
        }

        _state.value = ConnectionState.CONNECTING
        _error.value = null

        val device = try {
            btAdapter.getRemoteDevice(objetivo.address)
        } catch (e: IllegalArgumentException) {
            _state.value = ConnectionState.DISCONNECTED
            _error.value = "Direccion Bluetooth invalida: ${objetivo.address}"
            return
        }

        val tipo = if (objetivo.type != BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
            objetivo.type
        } else {
            try {
                device.type
            } catch (e: SecurityException) {
                BluetoothDevice.DEVICE_TYPE_UNKNOWN
            }
        }

        when (tipo) {
            BluetoothDevice.DEVICE_TYPE_LE -> {
                Log.d(TAG, "Transporte BLE para ${device.address}")
                connectBle(appContext, device)
            }
            else -> {
                Log.d(TAG, "Transporte CLASICO (SPP) para ${device.address} tipo=$tipo")
                connectClassic(appContext, device)
            }
        }
    }

    /** En clasico empareja (createBond); en BLE conecta directo. */
    @SuppressLint("MissingPermission")
    fun pair(context: Context, address: String) {
        val appContext = context.applicationContext
        val info = _discovered.value.firstOrNull { it.address == address }
        val btAdapter = adapter(appContext)
        val tipo = info?.type ?: try {
            btAdapter?.getRemoteDevice(address)?.type
        } catch (e: Exception) {
            BluetoothDevice.DEVICE_TYPE_UNKNOWN
        } ?: BluetoothDevice.DEVICE_TYPE_UNKNOWN

        if (tipo == BluetoothDevice.DEVICE_TYPE_LE) {
            connect(appContext, address)
            return
        }

        if (!hasConnectPermission(appContext)) {
            _error.value = "Permiso Bluetooth no concedido."
            return
        }
        val device = try {
            btAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (device == null) {
            _error.value = "Direccion Bluetooth invalida: $address"
            return
        }
        stopDiscovery(appContext)
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connect(appContext, address)
            return
        }
        registerReceiver(appContext)
        pendingPairAddress = device.address
        Log.d(TAG, "createBond() para ${device.address}")
        val ok = try {
            device.createBond()
        } catch (e: SecurityException) {
            false
        }
        if (!ok) {
            pendingPairAddress = null
            _error.value = "No se pudo iniciar el emparejamiento de ${device.address}."
        }
    }

    // === CLASICO SPP ===

    @SuppressLint("MissingPermission")
    private fun connectClassic(context: Context, device: BluetoothDevice) {
        scope.launch {
            try {
                adapter(context)?.cancelDiscovery()
            } catch (_: Exception) {
            }

            val fallos = mutableListOf<String>()
            for (intento in 1..6) {
                val metodo = when (intento) {
                    1, 2 -> "seguro"
                    3, 4 -> "insecure"
                    5 -> "canal1"
                    else -> "canal1-insecure"
                }
                try {
                    closeSocket()
                    val s = crearSocket(device, intento)
                    try {
                        s.connect()
                    } catch (e: Exception) {
                        try {
                            s.close()
                        } catch (_: Exception) {
                        }
                        throw e
                    }
                    socket = s
                    main.post { _state.value = ConnectionState.CONNECTED }
                    readLoopClassic(s)
                    return@launch
                } catch (e: Exception) {
                    fallos.add("$metodo: ${e.message ?: e.javaClass.simpleName}")
                    Log.w(TAG, "SPP intento $intento fallo: ${e.message}")
                    closeSocket()
                    if (intento < 6) delay(if (intento <= 2) 1500L else 2000L)
                }
            }
            main.post {
                _state.value = ConnectionState.DISCONNECTED
                _error.value = "No se pudo conectar (SPP) al HC-05 tras 6 intentos. " +
                    "Detalle: ${fallos.joinToString(" | ")}"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun crearSocket(device: BluetoothDevice, intento: Int): BluetoothSocket {
        val uuid = UUID.fromString(SPP_UUID)
        return when (intento) {
            1, 2 -> device.createRfcommSocketToServiceRecord(uuid)
            3, 4 -> device.createInsecureRfcommSocketToServiceRecord(uuid)
            5 -> socketPorReflexion(device, "createRfcommSocket", 1)
            else -> socketPorReflexion(device, "createInsecureRfcommSocket", 1)
        }
    }

    private fun socketPorReflexion(device: BluetoothDevice, nombreMetodo: String, canal: Int): BluetoothSocket {
        val metodo = device.javaClass.getMethod(nombreMetodo, Int::class.javaPrimitiveType)
        return metodo.invoke(device, canal) as BluetoothSocket
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLoopClassic(s: BluetoothSocket) {
        try {
            val input = s.inputStream
            val buf = ByteArray(1024)
            val texto = StringBuilder()
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                texto.append(String(buf, 0, n, Charsets.UTF_8))
                var idx = texto.indexOf("\n")
                while (idx >= 0) {
                    val linea = texto.substring(0, idx).trim()
                    texto.delete(0, idx + 1)
                    if (linea.isNotEmpty()) _incoming.tryEmit(linea)
                    idx = texto.indexOf("\n")
                }
                if (texto.length > 512) texto.setLength(0)
            }
        } catch (_: Exception) {
        }
        closeSocket()
        main.post { _state.value = ConnectionState.DISCONNECTED }
    }

    private fun closeSocket() {
        try {
            socket?.inputStream?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    // === BLE GATT ===

    @SuppressLint("MissingPermission")
    private fun connectBle(context: Context, device: BluetoothDevice) {
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _state.value = ConnectionState.DISCONNECTED
            _error.value = "Sin permiso para BLE: ${e.message}"
            return
        }
        if (gatt == null) {
            _state.value = ConnectionState.DISCONNECTED
            _error.value = "No se pudo iniciar la conexion BLE."
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "BLE onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    charUart = null
                    g.close()
                    if (gatt === g) gatt = null
                    main.post {
                        _state.value = ConnectionState.DISCONNECTED
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            _error.value = "Conexion BLE terminada (status $status)."
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                main.post { _error.value = "Descubrimiento de servicios fallo (status $status)" }
                return
            }
            val caracteristica = g.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            if (caracteristica == null) {
                main.post {
                    _error.value = "El modulo no expone el UART BLE (FFE0/FFE1)."
                    _state.value = ConnectionState.DISCONNECTED
                }
                g.disconnect()
                return
            }
            charUart = caracteristica
            g.setCharacteristicNotification(caracteristica, true)
            val cccd = caracteristica.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            } else {
                main.post { _state.value = ConnectionState.CONNECTED }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                main.post { _state.value = ConnectionState.CONNECTED }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            accumulate(characteristic.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            accumulate(value)
        }
    }

    private fun accumulate(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        buffer.append(String(bytes, Charsets.UTF_8))
        while (true) {
            val idx = buffer.indexOf("\n")
            if (idx < 0) break
            val linea = buffer.substring(0, idx).trim()
            buffer.delete(0, idx + 1)
            if (linea.isNotEmpty()) _incoming.tryEmit(linea)
        }
        if (buffer.length > 512) buffer.setLength(0)
    }

    // === ENVIO / CIERRE ===

    @SuppressLint("MissingPermission")
    fun send(command: String) {
        val c = charUart
        val g = gatt
        if (c != null && g != null) {
            val bytes = "$command\n".toByteArray(Charsets.UTF_8)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    c.value = bytes
                    @Suppress("DEPRECATION")
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(c)
                }
            } catch (e: SecurityException) {
                _error.value = "Error al enviar comando BLE: ${e.message}"
            }
            return
        }

        val s = socket ?: return
        scope.launch {
            try {
                s.outputStream.write(("$command\n").toByteArray(Charsets.UTF_8))
                s.outputStream.flush()
            } catch (e: Exception) {
                _error.value = "Error al enviar comando: ${e.message}"
                closeSocket()
                main.post { _state.value = ConnectionState.DISCONNECTED }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        charUart = null
        closeSocket()
        _state.value = ConnectionState.DISCONNECTED
    }
}
