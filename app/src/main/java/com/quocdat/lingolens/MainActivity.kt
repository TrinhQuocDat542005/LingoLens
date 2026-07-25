package com.quocdat.lingolens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quocdat.lingolens.navigation.AppNavigation
import com.quocdat.lingolens.ui.theme.LingoLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LingoLensTheme {
                AppNavigation()
            }
        }
    }
}