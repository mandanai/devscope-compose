package dev.journeyscope.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.JourneyScopeInspector
import dev.journeyscope.ui.demo.DemoJourneys
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.theme.defaultJourneyColors
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private enum class InspectorMode { Closed, Live, Demo }

@Composable
fun App() {
    MaterialTheme {
        LaunchedEffect(Unit) { enableJourneyScopeForSample() }
        var mode by rememberSaveable { mutableStateOf(InspectorMode.Closed) }
        when (mode) {
            InspectorMode.Closed -> SampleHome(onOpen = { mode = it })
            InspectorMode.Live -> JourneyScopeInspector(onClose = { mode = InspectorMode.Closed })
            InspectorMode.Demo -> JourneyScopeInspector(
                source = DemoJourneys.source,
                thresholds = InspectorThresholds(slowJourney = 1.seconds),
                colors = defaultJourneyColors(),
                onClose = { mode = InspectorMode.Closed },
            )
        }
    }
}

@Composable
private fun SampleHome(onOpen: (InspectorMode) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("JourneyScope Sample", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { scope.launch { runAddToCartJourney() } }) { Text("Run sample journey") }
            Button(onClick = { onOpen(InspectorMode.Live) }) { Text("Open Inspector") }
            OutlinedButton(onClick = { onOpen(InspectorMode.Demo) }) { Text("Open Inspector (demo data)") }
        }
    }
}
