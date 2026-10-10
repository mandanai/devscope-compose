package dev.journeyscope.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.JourneyScopeInspector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        var inspectorVisible by rememberSaveable { mutableStateOf(false) }
        if (inspectorVisible) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Inspector") },
                        navigationIcon = {
                            TextButton(onClick = { inspectorVisible = false }) { Text("Back") }
                        },
                    )
                },
            ) { innerPadding ->
                JourneyScopeInspector(modifier = Modifier.padding(innerPadding))
            }
        } else {
            Scaffold { innerPadding ->
                SampleHome(
                    onOpenInspector = { inspectorVisible = true },
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun SampleHome(onOpenInspector: () -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("JourneyScope Sample", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onOpenInspector) { Text("Open Inspector") }
    }
}
