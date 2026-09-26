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

    var showDevices by remember { mutableStateOf(false) }

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
