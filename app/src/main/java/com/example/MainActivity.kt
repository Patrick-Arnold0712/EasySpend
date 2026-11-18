package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ai.configureAppCheck
import com.example.ui.EasySpendApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            EasySpendApp()
        }
    }
}
