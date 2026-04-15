package com.patslaurel.resibo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.patslaurel.resibo.ui.navigation.ResiboNavGraph
import com.patslaurel.resibo.ui.theme.ResiboTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResiboTheme {
                ResiboNavGraph()
            }
        }
    }
}
