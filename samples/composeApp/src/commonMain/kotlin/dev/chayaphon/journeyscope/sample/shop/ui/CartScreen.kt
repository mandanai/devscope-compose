package dev.chayaphon.journeyscope.sample.shop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    Surface(modifier = modifier.fillMaxSize(), color = Canvas) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back", color = Violet, fontWeight = FontWeight.SemiBold) }
            Text("Your cart", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Violet, Pink, Orange)))
                        .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        viewModel.product.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Text(
                    "${state.cartCount} × ${viewModel.product.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.testTag("cart_items"),
                )
            }
        }
    }
}
