package dev.chayaphon.journeyscope.sample.shop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chayaphon.journeyscope.flow.LifecycleSource
import dev.chayaphon.journeyscope.flow.observeWithJourneyScope

@Composable
fun CartScreen(
    viewModel: ProductViewModel,
    onBack: () -> Unit,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
    lifecycleSource: LifecycleSource? = null,
) {
    var state by remember { mutableStateOf(viewModel.state.value) }
    LaunchedEffect(viewModel, lifecycleSource) {
        viewModel.state.observeWithJourneyScope("cart-screen-state", viewModel.traceOptions(lifecycleSource)) { state = it }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onShown()
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Your cart", style = MaterialTheme.typography.headlineMedium)
            Text("${state.cartCount} × ${viewModel.product.name}", modifier = Modifier.testTag("cart_items"))
        }
    }
}
