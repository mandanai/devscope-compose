package dev.journeyscope.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.theme.JourneyTheme

@Composable
private fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
internal fun JourneyEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = JourneyTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = JourneyTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun JourneyErrorState(reason: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredMessage(modifier) {
        Text("Couldn't load journeys", style = MaterialTheme.typography.titleMedium, color = JourneyTheme.colors.failure)
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = JourneyTheme.colors.textSecondary, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
internal fun JourneyLoadingState(modifier: Modifier = Modifier) {
    CenteredMessage(modifier.semantics { contentDescription = "Loading journeys" }) {
        CircularProgressIndicator()
    }
}
