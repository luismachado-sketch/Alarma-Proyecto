package com.example.alarmaproyecto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.alarmaproyecto.navigation.AppNavigation
import com.example.alarmaproyecto.notifications.NotificationHelper
import com.example.alarmaproyecto.ui.theme.AlarmaProyectoTheme
import com.example.alarmaproyecto.ui.viewmodel.AlarmViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createChannel(this)
        setContent {
            AlarmaProyectoTheme {
                val navController = rememberNavController()
                val alarmViewModel: AlarmViewModel = viewModel()
                AppNavigation(
                    navController = navController,
                    alarmViewModel = alarmViewModel
                )
            }
        }
    }
}
