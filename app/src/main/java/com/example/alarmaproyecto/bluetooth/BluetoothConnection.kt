package com.example.alarmaproyecto.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

data class PairedDeviceInfo(
    val name: String,
    val address: String
)

/**
 * Conexion Bluetooth clasica (SPP) con el modulo HC-05.
 * Singleton: estado y lineas recibidas como Flows.
 */
object BluetoothConnection {

    private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: BluetoothSocket? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() {
        _error.value = null
    }

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun hasConnectPermission(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    /** Lista los dispositivos ya emparejados (para diagnostico y conexion manual). */
    @SuppressLint("MissingPermission")
    fun bondedDevices(context: Context): List<PairedDeviceInfo> {
        val appContext = context.applicationContext
        if (!hasConnectPermission(appContext)) return emptyList()

        val btAdapter = adapter(appContext) ?: return emptyList()
        return try {
            (btAdapter.bondedDevices ?: emptySet())
                .map { PairedDeviceInfo(it.name ?: "Sin nombre", it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * Conecta con el HC-05 emparejado.
     * @param address MAC especifica; null = busca automaticamente "HC-05".
     * Reintenta 2 veces porque el HC-05 a veces rechaza la primera conexion.
     */
    @SuppressLint("MissingPermission")
    fun connect(context: Context, address: String? = null) {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.CONNECTED) {
            return
        }

        val appContext = context.applicationContext

        if (!hasConnectPermission(appContext)) {
            _error.value = "Permiso Bluetooth no concedido. Activa el permiso en Ajustes."
            return
        }

        val btAdapter = adapter(appContext)
        if (btAdapter == null) {
            _error.value = "Bluetooth no disponible en este dispositivo"
            return
        }
        if (!btAdapter.isEnabled) {
            _error.value = "Bluetooth apagado. Activa el Bluetooth en Ajustes."
            return
        }

        _state.value = ConnectionState.CONNECTING
        _error.value = null

        scope.launch {
            // Un descubrimiento activo (de otra app) impide conectar
            try {
                btAdapter.cancelDiscovery()
            } catch (_: Exception) {
            }

            var ultimoMensaje = "error desconocido"

            // 4 intentos: 2 con socket seguro (SPP) y 2 con socket insecure
            for (intento in 1..4) {
                try {
                    val device: BluetoothDevice = if (address != null) {
                        btAdapter.getRemoteDevice(address)
                    } else {
                        val bonded: Set<BluetoothDevice> = try {
                            btAdapter.bondedDevices ?: emptySet()
                        } catch (e: SecurityException) {
                            emptySet()
                        }

                        val hc05 = bonded.firstOrNull { d ->
                            val name = d.name ?: ""
                            name.startsWith("HC-05", ignoreCase = true) ||
                                name.startsWith("HC05", ignoreCase = true)
                        }

                        if (hc05 == null) {
                            val nombres = bonded.joinToString { it.name ?: "sin nombre" }
                            _state.value = ConnectionState.DISCONNECTED
                            _error.value = if (bonded.isEmpty()) {
                                "HC-05 no emparejado. Ve a Ajustes > Bluetooth y emparejalo con PIN 1234."
                            } else {
                                "HC-05 no encontrado. Dispositivos emparejados: $nombres"
                            }
                            return@launch
                        }
                        hc05
                    }

                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        _state.value = ConnectionState.DISCONNECTED
                        _error.value = "El dispositivo ${device.name ?: device.address} no esta " +
                            "vinculado. Emparejalo desde Ajustes > Bluetooth con PIN 1234."
                        return@launch
                    }

                    closeSocket()

                    val uuid = UUID.fromString(SPP_UUID)
                    val s = if (intento <= 2) {
                        device.createRfcommSocketToServiceRecord(uuid)
                    } else {
                        device.createInsecureRfcommSocketToServiceRecord(uuid)
                    }
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
                    _state.value = ConnectionState.CONNECTED
                    readLoop(s)
                    // readLoop retorno: la conexion se cerro desde el otro lado
                    return@launch
                } catch (e: Exception) {
                    ultimoMensaje = e.message ?: e.javaClass.simpleName
                    closeSocket()
                    if (intento < 4) {
                        kotlinx.coroutines.delay(if (intento == 1) 1000L else 2000L)
                    }
                }
            }

            _state.value = ConnectionState.DISCONNECTED
            _error.value = "No se pudo conectar tras 4 intentos ($ultimoMensaje). " +
                "Causa habitual: otra app (ej. Serial Bluetooth) ya tiene el HC-05 conectado - " +
                "solo admite UNA conexion: cierra las demas apps Bluetooth y reintenta. " +
                "Si persiste: borra el emparejado del telefono, reinicia el HC-05 " +
                "y vuelve a emparejar con PIN 1234."
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLoop(s: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream, "UTF-8"))
            while (true) {
                val line = reader.readLine() ?: break
                val limpia = line.trim()
                if (limpia.isNotEmpty()) {
                    _incoming.emit(limpia)
                }
            }
        } catch (_: Exception) {
            // Conexion cerrada o perdida
        }
        socket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    /** Envia un comando con salto de linea (el Arduino usa readStringUntil('\n')). */
    fun send(command: String) {
        val s = socket ?: return
        scope.launch {
            try {
                s.outputStream.write(("$command\n").toByteArray(Charsets.UTF_8))
                s.outputStream.flush()
            } catch (e: Exception) {
                _error.value = "Error al enviar comando: ${e.message}"
                closeSocket()
                _state.value = ConnectionState.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        scope.launch {
            closeSocket()
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }
}
