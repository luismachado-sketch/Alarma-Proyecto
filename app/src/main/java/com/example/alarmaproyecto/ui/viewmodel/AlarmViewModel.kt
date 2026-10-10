package com.example.alarmaproyecto.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmaproyecto.bluetooth.BluetoothConnection
import com.example.alarmaproyecto.bluetooth.ConnectionState
import com.example.alarmaproyecto.bluetooth.PairedDeviceInfo
import com.example.alarmaproyecto.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Estado compartido de la alarma entre las pestañas Dashboard y Control.
 * Parsea los mensajes del Arduino y envia comandos CMD:*.
 */
class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed

    private val _siren = MutableStateFlow(false)
    val siren: StateFlow<Boolean> = _siren

    private val _light = MutableStateFlow(false)
    val light: StateFlow<Boolean> = _light

    private val _motion = MutableStateFlow(false)
    val motion: StateFlow<Boolean> = _motion

    private val _motionTime = MutableStateFlow<String?>(null)
    val motionTime: StateFlow<String?> = _motionTime

    private val _alertTick = MutableStateFlow(0L)
    val alertTick: StateFlow<Long> = _alertTick

    private val _pairedDevices = MutableStateFlow<List<PairedDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDeviceInfo>> = _pairedDevices

    val discoveredDevices: StateFlow<List<PairedDeviceInfo>> = BluetoothConnection.discovered
    val discovering: StateFlow<Boolean> = BluetoothConnection.discovering

    val connectionState: StateFlow<ConnectionState> = BluetoothConnection.state
    val connectionError: StateFlow<String?> = BluetoothConnection.error

    private var sincronizado = false

    init {
        viewModelScope.launch {
            BluetoothConnection.incoming.collect { linea -> manejarLinea(linea) }
        }
        viewModelScope.launch {
            BluetoothConnection.state.collect { estado ->
                if (estado == ConnectionState.CONNECTED && !sincronizado) {
                    sincronizado = true
                    BluetoothConnection.send("CMD:status")
                } else if (estado != ConnectionState.CONNECTED) {
                    sincronizado = false
                }
            }
        }
    }

    fun connect() = BluetoothConnection.connect(getApplication())

    fun connectTo(address: String) = BluetoothConnection.connect(getApplication(), address)

    fun refreshPairedDevices() {
        _pairedDevices.value = BluetoothConnection.bondedDevices(getApplication())
    }

    fun startDiscovery() = BluetoothConnection.startDiscovery(getApplication())

    fun stopDiscovery() = BluetoothConnection.stopDiscovery(getApplication())

    fun pairDevice(address: String) = BluetoothConnection.pair(getApplication(), address)

    fun disconnect() = BluetoothConnection.disconnect()

    fun clearError() = BluetoothConnection.clearError()

    fun setArmed(valor: Boolean) {
        _armed.value = valor
        BluetoothConnection.send(if (valor) "CMD:arm:1" else "CMD:arm:0")
    }

    fun setSiren(valor: Boolean) {
        _siren.value = valor
        BluetoothConnection.send(if (valor) "CMD:siren:1" else "CMD:siren:0")
    }

    fun setLight(valor: Boolean) {
        _light.value = valor
        BluetoothConnection.send(if (valor) "CMD:light:1" else "CMD:light:0")
    }

    private fun manejarLinea(linea: String) {
        val partes = linea.split(":")
        when {
            linea.startsWith("STATUS:armed:") && partes.size >= 3 -> {
                _armed.value = partes[2] == "1"
            }
            linea.startsWith("STATUS:siren:") && partes.size >= 3 -> {
                _siren.value = partes[2] == "1"
            }
            linea.startsWith("STATUS:light:") && partes.size >= 3 -> {
                _light.value = partes[2] == "1"
            }
            linea.startsWith("SENSOR:motion:") && partes.size >= 3 -> {
                val hayMovimiento = partes[2] == "1"
                _motion.value = hayMovimiento
                if (hayMovimiento) {
                    _motionTime.value = horaActual()
                }
            }
            linea.startsWith("ALERT:motion:") && partes.size >= 3 -> {
                _motion.value = true
                _motionTime.value = horaActual()
                _alertTick.value = System.currentTimeMillis()
                NotificationHelper.showMotionAlert(getApplication())
            }
        }
    }

    private fun horaActual(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
