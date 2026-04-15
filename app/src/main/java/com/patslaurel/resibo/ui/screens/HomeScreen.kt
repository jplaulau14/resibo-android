package com.patslaurel.resibo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patslaurel.resibo.share.ShareIntents
import com.patslaurel.resibo.ui.theme.ResiboTheme

@Composable
fun HomeScreen(
    onOpenNote: () -> Unit = {},
    onOpenTrace: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPlayground: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Resibo",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "On-device fact-check Notes for Filipinos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onOpenPlayground,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Playground (free-text Gemma)")
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { ShareIntents.launchTextTest(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Run canned test (COVID-autism claim)")
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onOpenNote,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sample Note")
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onOpenTrace,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reasoning Trace")
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Settings")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ResiboTheme {
        HomeScreen()
    }
}
