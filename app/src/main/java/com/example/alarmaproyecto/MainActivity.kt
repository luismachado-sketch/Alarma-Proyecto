package com.example.alarmaproyecto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.alarmaproyecto.navigation.AppNavigation
import com.example.alarmaproyecto.ui.theme.AlarmaProyectoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlarmaProyectoTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
