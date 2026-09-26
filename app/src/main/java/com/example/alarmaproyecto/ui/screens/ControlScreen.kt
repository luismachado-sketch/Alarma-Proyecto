package com.example.alarmaproyecto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmaproyecto.bluetooth.ConnectionState
import com.example.alarmaproyecto.ui.viewmodel.AlarmViewModel

@Composable
fun ControlScreen(
    viewModel: AlarmViewModel,
    modifier: Modifier = Modifier
) {
    val connection by viewModel.connectionState.collectAsState()
    val armed by viewModel.armed.collectAsState()
    val siren by viewModel.siren.collectAsState()
    val light by viewModel.light.collectAsState()

    val conectado = connection == ConnectionState.CONNECTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!conectado) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Text(
                    text = "Sin conexion Bluetooth. Ve a la pestana Dashboard y conecta al HC-05 para controlar la alarma.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = Color(0xFFE65100)
                )
            }
        }

        // === BOTON ARMAR / DESARMAR ===
        Button(
            onClick = { viewModel.setArmed(!armed) },
            enabled = conectado,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (armed) Color(0xFFC62828) else Color(0xFF2E7D32)
            )
        ) {
            Text(
                text = if (armed) "DESACTIVAR ALARMA" else "ACTIVAR ALARMA",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Text(
            text = if (armed) "Sistema armado: el PIR activara sirena y luz" else "Sistema desarmado: el PIR no dispara alarmas",
            fontSize = 13.sp,
            color = Color.Gray
        )

        // === SWITCH SIRENA ===
        ControlSwitchCard(
            icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFC62828)) },
            titulo = "Sirena",
            subtitulo = "Relay 1 - salida D7",
            checked = siren,
            enabled = conectado,
            onCheckedChange = { viewModel.setSiren(it) }
        )

        // === SWITCH LUZ ===
        ControlSwitchCard(
            icon = { Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF9A825)) },
            titulo = "Luz",
            subtitulo = "Relay 2 - salida D8",
            checked = light,
            enabled = conectado,
            onCheckedChange = { viewModel.setLight(it) }
        )
    }
}

@Composable
private fun ControlSwitchCard(
    icon: @Composable () -> Unit,
    titulo: String,
    subtitulo: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitulo,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
