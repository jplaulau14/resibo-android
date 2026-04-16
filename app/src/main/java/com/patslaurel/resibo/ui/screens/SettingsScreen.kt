package com.patslaurel.resibo.ui.screens

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                supportingContent = { Text("Fully offline. No sign-in. No telemetry. No data leaves your phone.") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("0.1.0-dev") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(
                        "Resibo is a hackathon project for the Gemma 4 Good Hackathon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
            )
        }
    }
}
