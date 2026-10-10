package dev.journeyscope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.journeyscope.ui.model.JourneyCategory

@Immutable
public data class JourneyColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val uiVm: Color,
    val network: Color,
    val storage: Color,
    val render: Color,
    val other: Color,
    val idle: Color,
    val bottleneck: Color,
    val bottleneckContainer: Color,
    val failure: Color,
    val failureContainer: Color,
    val running: Color,
    val runningContainer: Color,
    val success: Color,
    val successContainer: Color,
) {
    public fun categoryColor(category: JourneyCategory): Color = when (category) {
        JourneyCategory.UiVm -> uiVm
        JourneyCategory.Network -> network
        JourneyCategory.Storage -> storage
        JourneyCategory.Render -> render
        JourneyCategory.Other -> other
        JourneyCategory.Idle -> idle
    }

    public companion object {
        public fun light(): JourneyColors = JourneyColors(
            isDark = false,
            background = Color(0xFFF4F5F7),
            surface = Color(0xFFFFFFFF),
            border = Color(0xFFE2E5EA),
            divider = Color(0xFFE9ECF0),
            textPrimary = Color(0xFF14181F),
            textSecondary = Color(0xFF5A6372),
            uiVm = Color(0xFF2F6FED),
            network = Color(0xFFF2740F),
            storage = Color(0xFF0F9488),
            render = Color(0xFF7C4DDB),
            other = Color(0xFF6B7280),
            idle = Color(0xFFC3C9D2),
            bottleneck = Color(0xFFB63D00),
            bottleneckContainer = Color(0xFFFFEDE2),
            failure = Color(0xFFB3261E),
            failureContainer = Color(0xFFFDE8E6),
            running = Color(0xFF1F5FD6),
            runningContainer = Color(0xFFE6EEFD),
            success = Color(0xFF1B7A43),
            successContainer = Color(0xFFE3F4EA),
        )

        public fun dark(): JourneyColors = JourneyColors(
            isDark = true,
            background = Color(0xFF0E1013),
            surface = Color(0xFF181B20),
            border = Color(0xFF2B3038),
            divider = Color(0xFF262B32),
            textPrimary = Color(0xFFF1F3F6),
            textSecondary = Color(0xFFA6AEBB),
            uiVm = Color(0xFF6B9BFF),
            network = Color(0xFFFF9447),
            storage = Color(0xFF3CC9BB),
            render = Color(0xFFA98BF5),
            other = Color(0xFF9AA3B2),
            idle = Color(0xFF4A515C),
            bottleneck = Color(0xFFFFA06B),
            bottleneckContainer = Color(0xFF3A2414),
            failure = Color(0xFFFF8A80),
            failureContainer = Color(0xFF3F1B19),
            running = Color(0xFF8CB1FF),
            runningContainer = Color(0xFF1B2A47),
            success = Color(0xFF6FD39A),
            successContainer = Color(0xFF173626),
        )
    }
}

internal val LocalJourneyColors = staticCompositionLocalOf { JourneyColors.light() }

internal object JourneyTheme {
    val colors: JourneyColors
        @Composable @ReadOnlyComposable get() = LocalJourneyColors.current
}

@Composable
public fun JourneyScopeTheme(
    colors: JourneyColors = defaultJourneyColors(),
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit,
) {
    val scheme = remember(colors) {
        val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.uiVm,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            outlineVariant = colors.divider,
            error = colors.failure,
        )
    }
    CompositionLocalProvider(LocalJourneyColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

@Composable
public fun defaultJourneyColors(): JourneyColors {
    val dark = isSystemInDarkTheme()
    return remember(dark) { if (dark) JourneyColors.dark() else JourneyColors.light() }
}
