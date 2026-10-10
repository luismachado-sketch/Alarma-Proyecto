package com.example.alarmaproyecto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmaproyecto.bluetooth.ConnectionState
import com.example.alarmaproyecto.ui.theme.IoTPrimary
import com.example.alarmaproyecto.ui.viewmodel.AlarmViewModel

private val ColorVerde = Color(0xFF2E7D32)
private val ColorRojo = Color(0xFFC62828)
private val ColorAmbar = Color(0xFFF9A825)
private val ColorGris = Color(0xFF757575)

@Composable
fun DashboardContent(
    userEmail: String,
    viewModel: AlarmViewModel,
    modifier: Modifier = Modifier
) {
    val connection by viewModel.connectionState.collectAsState()
    val armed by viewModel.armed.collectAsState()
    val siren by viewModel.siren.collectAsState()
    val light by viewModel.light.collectAsState()
    val motion by viewModel.motion.collectAsState()
    val motionTime by viewModel.motionTime.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val discovering by viewModel.discovering.collectAsState()

    var showDevices by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var manualMac by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === FILA DE CONEXION BLUETOOTH ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EstadoChip(
                texto = when (connection) {
                    ConnectionState.CONNECTED -> "Conectado"
                    ConnectionState.CONNECTING -> "Conectando..."
                    ConnectionState.DISCONNECTED -> "Desconectado"
                },
                color = when (connection) {
                    ConnectionState.CONNECTED -> ColorVerde
                    ConnectionState.CONNECTING -> ColorAmbar
                    ConnectionState.DISCONNECTED -> ColorRojo
                }
            )
            Button(
                onClick = {
                    if (connection == ConnectionState.CONNECTED) {
                        viewModel.disconnect()
                    } else {
                        viewModel.connect()
                    }
                },
                enabled = connection != ConnectionState.CONNECTING,
                colors = ButtonDefaults.buttonColors(containerColor = IoTPrimary)
            ) {
                Text(if (connection == ConnectionState.CONNECTED) "Desconectar" else "Conectar")
            }
        }

        // === BOTON BUSCAR/EMPAREJAR (necesario en Samsung con HC-05/HC-06) ===
        Button(
            onClick = {
                viewModel.startDiscovery()
                showScan = true
            },
            enabled = connection != ConnectionState.CONNECTING,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = IoTPrimary)
        ) {
            Text("Buscar modulo Bluetooth (HC-05 / HC-06)")
        }

        // === BOTON VER DISPOSITIVOS ===
        OutlinedButton(
            onClick = {
                viewModel.refreshPairedDevices()
                showDevices = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ver dispositivos Bluetooth emparejados")
        }

        Text(
            text = "Sesion: $userEmail",
            fontSize = 13.sp,
            color = ColorGris
        )

        // === TARJETA SISTEMA ===
        StatusCard(
            titulo = "Sistema",
            valor = if (armed) "ARMADO" else "DESARMADO",
            colorValor = if (armed) ColorRojo else ColorVerde,
            detalle = if (armed) "El PIR activara sirena y luz al detectar movimiento" else "El PIR ignora el movimiento"
        )

        // === TARJETA MOVIMIENTO ===
        StatusCard(
            titulo = "Movimiento",
            valor = if (motion) "SI DETECTADO" else "NO",
            colorValor = if (motion) ColorRojo else ColorGris,
            detalle = motionTime?.let { "Ultima deteccion: $it" } ?: "Sin detecciones aun"
        )

        // === TARJETAS SIRENA Y LUZ ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                titulo = "Sirena",
                valor = if (siren) "ON" else "OFF",
                colorValor = if (siren) ColorRojo else ColorGris,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                titulo = "Luz",
                valor = if (light) "ON" else "OFF",
                colorValor = if (light) ColorAmbar else ColorGris,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // === DIALOGO DE DISPOSITIVOS EMPAREJADOS ===
    if (showDevices) {
        AlertDialog(
            onDismissRequest = { showDevices = false },
            title = { Text("Dispositivos emparejados") },
            text = {
                if (pairedDevices.isEmpty()) {
                    Text(
                        "No hay dispositivos emparejados (o falta el permiso Bluetooth). " +
                            "Ve a Ajustes > Bluetooth y empareja el HC-05 con PIN 1234."
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        pairedDevices.forEach { dispositivo ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.connectTo(dispositivo.address)
                                        showDevices = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = dispositivo.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = dispositivo.address,
                                    fontSize = 12.sp,
                                    color = ColorGris
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevices = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // === DIALOGO DE ESCANEO Y EMPAREJAMIENTO ===
    if (showScan) {
        AlertDialog(
            onDismissRequest = {
                viewModel.stopDiscovery()
                showScan = false
            },
            title = { Text("Buscar modulo Bluetooth") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (discovering) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text("Buscando dispositivos...", fontSize = 13.sp)
                        }
                    }
                    Text(
                        text = "Toca el modulo para conectarte. El HC-05 (clasico) pide PIN " +
                            "(prueba 1234). Los modulos BLE tipo HM-10 conectan sin PIN.",
                        fontSize = 12.sp,
                        color = ColorGris,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (discoveredDevices.isEmpty() && !discovering) {
                        Text(
                            "No se encontraron dispositivos. Verifica que el modulo este " +
                                "encendido (LED parpadeando) y vuelve a escanear."
                        )
                    } else {
                        discoveredDevices.forEach { dispositivo ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.pairDevice(dispositivo.address)
                                        viewModel.stopDiscovery()
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = dispositivo.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = dispositivo.address,
                                    fontSize = 12.sp,
                                    color = ColorGris
                                )
                            }
                            HorizontalDivider()
                        }
                    }

                    // Conexion manual por direccion MAC (si el escaneo no lo lista)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Conectar por direccion MAC (BLE):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = manualMac,
                        onValueChange = { manualMac = it },
                        label = { Text("Ej: 25:FB:A3:A6:E9:D2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val mac = manualMac.trim()
                            if (mac.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))) {
                                viewModel.connectTo(mac)
                                viewModel.stopDiscovery()
                            } else {
                                manualMac = ""
                            }
                        },
                        enabled = manualMac.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Conectar por MAC (BLE)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.stopDiscovery()
                    showScan = false
                }) {
                    Text("Cerrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.startDiscovery() }) {
                    Text("Escanear de nuevo")
                }
            }
        )
    }
}

@Composable
private fun EstadoChip(texto: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Text(
                text = texto,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
private fun StatusCard(
    titulo: String,
    valor: String,
    colorValor: Color,
    detalle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = titulo,
                fontSize = 14.sp,
                color = ColorGris
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorValor
            )
            if (detalle != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = detalle,
                    fontSize = 12.sp,
                    color = ColorGris
                )
            }
        }
    }
}
