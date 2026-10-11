package dev.chayaphon.journeyscope.sample.shop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chayaphon.journeyscope.flow.LifecycleSource
import dev.chayaphon.journeyscope.flow.observeWithJourneyScope
import dev.chayaphon.journeyscope.sample.shop.SampleScenario

@Composable
fun ProductDetailScreen(
    viewModel: ProductViewModel,
    onOpenInspector: (() -> Unit)?,
    onOpenDemo: (() -> Unit)?,
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

    Surface(modifier = modifier.fillMaxSize(), color = Canvas) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TopBar(state.cartCount, onOpenDemo, onOpenInspector)
            ProductHero(viewModel.product)
            StatusCard(state)
            ActionButtons(
                adding = state.phase == CartPhase.Adding,
                onAdd = { viewModel.addToCart(scenario) },
                onAddAndOpen = { viewModel.addToCart(scenario, openCartOnSuccess = true) },
                onOpenCart = onOpenCart,
                onCancel = viewModel::cancelAdd,
            )
            ScenarioPicker(scenario) { scenarioName = it.name }
        }
    }
}

@Composable
private fun TopBar(
    cartCount: Int,
    onOpenDemo: (() -> Unit)?,
    onOpenInspector: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.clip(CircleShape).background(Violet.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                "Cart · $cartCount",
                style = MaterialTheme.typography.labelLarge,
                color = Violet,
                modifier = Modifier.testTag("cart_count"),
            )
        }
        Row {
            onOpenDemo?.let { TextButton(onClick = it) { Text("Demo data", color = Violet) } }
            onOpenInspector?.let { TextButton(onClick = it) { Text("Inspector", color = Violet) } }
        }
    }
}

@Composable
private fun ProductHero(product: Product) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Violet, Pink, Orange)))
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                product.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(product.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(product.price, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.92f))
        }
    }
}

@Composable
private fun StatusCard(state: CartUiState) {
    val accent = phaseColor(state.phase)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        Text(
            statusLine(state),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            modifier = Modifier.testTag("cart_status"),
        )
    }
}

@Composable
private fun ActionButtons(
    adding: Boolean,
    onAdd: () -> Unit,
    onAddAndOpen: () -> Unit,
    onOpenCart: () -> Unit,
    onCancel: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
        ) { Text("Add to Cart", fontWeight = FontWeight.SemiBold) }
        Button(
            onClick = onAddAndOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(containerColor = Pink),
        ) { Text("Add & open cart", fontWeight = FontWeight.SemiBold) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onOpenCart,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = buttonShape,
                border = BorderStroke(1.5.dp, Violet),
            ) { Text("View cart", color = Violet) }
            OutlinedButton(
                onClick = onCancel,
                enabled = adding,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = buttonShape,
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun ScenarioPicker(
    selected: SampleScenario,
    onSelect: (SampleScenario) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Debug scenario", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Ink)
        SampleScenario.entries.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Violet.copy(alpha = 0.12f) else Color.White)
                        .border(1.dp, if (isSelected) Violet else Outline, RoundedCornerShape(14.dp))
                        .selectable(selected = isSelected, onClick = { onSelect(option) }, role = Role.RadioButton)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Violet))
                Text(option.label, color = Ink, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal val Canvas = Color(0xFFF7F5FF)
internal val Ink = Color(0xFF1B1530)
internal val Outline = Color(0xFFDAD5EE)
internal val Violet = Color(0xFF6C4CF1)
internal val Pink = Color(0xFFE5489B)
internal val Orange = Color(0xFFFF8A3D)
private val Green = Color(0xFF1FA971)
private val Amber = Color(0xFFE59A0B)
private val Red = Color(0xFFD64545)

private fun phaseColor(phase: CartPhase): Color =
    when (phase) {
        CartPhase.Idle -> Violet
        CartPhase.Adding -> Amber
        CartPhase.Added -> Green
        CartPhase.Failed, CartPhase.Cancelled -> Red
    }

private fun statusLine(state: CartUiState): String =
    when (state.phase) {
        CartPhase.Idle -> "Ready"
        CartPhase.Adding -> "Adding… (${state.pending} in flight)"
        CartPhase.Added -> "Added to cart"
        CartPhase.Failed, CartPhase.Cancelled -> state.message ?: "Failed"
    }
