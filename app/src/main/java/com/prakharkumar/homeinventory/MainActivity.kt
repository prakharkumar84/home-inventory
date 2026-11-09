package com.prakharkumar.homeinventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.prakharkumar.homeinventory.navigation.AppNavGraph
import com.prakharkumar.homeinventory.ui.theme.HomeInventoryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as InventoryApplication).container
        setContent {
            HomeInventoryTheme {
                Surface(color = Color.Unspecified) {
                    AppNavGraph(container = container)
                }
            }
        }
    }
}
