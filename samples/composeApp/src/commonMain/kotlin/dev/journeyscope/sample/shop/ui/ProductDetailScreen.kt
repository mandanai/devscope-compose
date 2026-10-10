package dev.journeyscope.sample.shop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.journeyscope.flow.LifecycleSource
import dev.journeyscope.flow.observeWithJourneyScope
import dev.journeyscope.sample.shop.SampleScenario

@Composable
fun ProductDetailScreen(
    viewModel: ProductViewModel,
    onOpenInspector: () -> Unit,
    onOpenDemo: () -> Unit,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier,
    lifecycleSource: LifecycleSource? = null,
) {
    var state by remember { mutableStateOf(viewModel.state.value) }
    LaunchedEffect(viewModel, lifecycleSource) {
        viewModel.state.observeWithJourneyScope("cart-ui-state", viewModel.traceOptions(lifecycleSource)) { state = it }
    }
    var scenarioName by rememberSaveable { mutableStateOf(SampleScenario.Normal.name) }
    val scenario = SampleScenario.valueOf(scenarioName)

    // Resumes at the next frame start, after Compose drew the frame holding this state; see onCartCountRendered.
    val renderToken = state.renderToken
    LaunchedEffect(renderToken) {
        withFrameNanos { }
        viewModel.onCartCountRendered(renderToken)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Cart: ${state.cartCount}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("cart_count"))
                Row {
                    TextButton(onClick = onOpenDemo) { Text("Demo data") }
                    TextButton(onClick = onOpenInspector) { Text("Inspector") }
                }
            }
            Text(viewModel.product.name, style = MaterialTheme.typography.headlineMedium)
            Text(viewModel.product.price, style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.addToCart(scenario) }) { Text("Add to Cart") }
                Button(onClick = { viewModel.addToCart(scenario, openCartOnSuccess = true) }) { Text("Add & open cart") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenCart) { Text("View cart") }
                OutlinedButton(onClick = viewModel::cancelAdd, enabled = state.phase == CartPhase.Adding) { Text("Cancel") }
            }
            Text(statusLine(state), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("cart_status"))

            Text("Debug scenario", style = MaterialTheme.typography.titleSmall)
            SampleScenario.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth().selectable(
                            selected = option == scenario,
                            onClick = { scenarioName = option.name },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == scenario, onClick = null)
                    Text(option.label, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun statusLine(state: CartUiState): String =
    when (state.phase) {
        CartPhase.Idle -> "Ready"
        CartPhase.Adding -> "Adding… (${state.pending} in flight)"
        CartPhase.Added -> "Added to cart"
        CartPhase.Failed, CartPhase.Cancelled -> state.message ?: "Failed"
    }
