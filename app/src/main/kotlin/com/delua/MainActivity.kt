package com.delua

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.delua.ui.navigation.DeluaNavHost
import com.delua.ui.theme.DeluaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeluaTheme {
                val navController = rememberNavController()
                DeluaNavHost(navController = navController)
            }
        }
    }
}
