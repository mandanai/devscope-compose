package dev.journeyscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.model.StatusKind
import dev.journeyscope.ui.model.StatusUi
import dev.journeyscope.ui.theme.JourneyTheme

@Composable
internal fun JourneyHeader(
    title: String,
    subtitle: String,
    status: StatusUi,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = JourneyTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = JourneyTheme.colors.textSecondary)
        }
        JourneyStatusBadge(status)
    }
}

@Composable
internal fun JourneyStatusBadge(
    status: StatusUi,
    modifier: Modifier = Modifier,
) {
    val colors = JourneyTheme.colors
    val (container, content) =
        when (status.kind) {
            StatusKind.Slow -> colors.bottleneckContainer to colors.bottleneck
            StatusKind.Failed -> colors.failureContainer to colors.failure
            StatusKind.Running -> colors.runningContainer to colors.running
            StatusKind.Completed -> colors.successContainer to colors.success
            StatusKind.Created, StatusKind.Cancelled, StatusKind.Interrupted -> colors.divider to colors.textSecondary
        }
    Row(
        modifier =
            modifier
                .background(container, RoundedCornerShape(percent = 50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Status: ${status.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(content, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text = status.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
    }
}
