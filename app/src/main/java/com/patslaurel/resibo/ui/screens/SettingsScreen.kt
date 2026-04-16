package com.patslaurel.resibo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.patslaurel.resibo.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showThemePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text("Appearance") },
                supportingContent = {
                    Text(
                        when (currentTheme) {
                            ThemeMode.SYSTEM -> "System default"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                    )
                },
                modifier = Modifier.clickable { showThemePicker = !showThemePicker },
            )
            if (showThemePicker) {
                ThemeMode.entries.forEach { mode ->
                    ListItem(
                        headlineContent = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System default"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = { onThemeChange(mode) },
                            )
                        },
                        modifier = Modifier.clickable { onThemeChange(mode) },
                    )
                }
            }
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Model") },
                supportingContent = { Text("Gemma 4 E4B (4.5B effective)") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Inference") },
                supportingContent = { Text("GPU (Adreno 660)") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Storage") },
                supportingContent = { Text("Encrypted (SQLCipher + Android Keystore)") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Privacy") },
                supportingContent = { Text("Fully offline. No sign-in. No telemetry.") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("0.1.0-dev") },
            )
        }
    }
}
