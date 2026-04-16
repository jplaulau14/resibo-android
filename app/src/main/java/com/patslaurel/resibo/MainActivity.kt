package com.patslaurel.resibo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patslaurel.resibo.ui.navigation.ResiboNavGraph
import com.patslaurel.resibo.ui.theme.ResiboTheme
import com.patslaurel.resibo.ui.theme.ThemePreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themePreference: ThemePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreference.themeMode.collectAsStateWithLifecycle()
            ResiboTheme(themeMode = themeMode) {
                ResiboNavGraph(
                    themePreference = themePreference,
                    currentTheme = themeMode,
                )
            }
        }
    }
}
