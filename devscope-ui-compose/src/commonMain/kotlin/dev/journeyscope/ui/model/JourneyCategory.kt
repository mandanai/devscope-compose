package dev.journeyscope.ui.model

import dev.journeyscope.core.model.SpanCategory

public enum class JourneyCategory(public val label: String) {
    UiVm("UI / VM"),
    Network("Network"),
    Storage("Storage"),
    Render("Render"),
    Other("Other"),
    Idle("Untracked"),
}

internal enum class Glyph {
    Tap,
    Layers,
    Flow,
    Cylinder,
    Globe,
    Check,
    Bolt,
    Window,
    Dot,
    Bulb,
    Back,
    Close,
    ChevronDown,
}

internal fun SpanCategory.toJourneyCategory(): JourneyCategory = when (this) {
    SpanCategory.UI_INTERACTION,
    SpanCategory.VIEW_MODEL,
    SpanCategory.USE_CASE,
    SpanCategory.REPOSITORY,
    SpanCategory.BUSINESS_VALIDATION,
    SpanCategory.STATE_EMISSION,
    -> JourneyCategory.UiVm
    SpanCategory.NETWORK -> JourneyCategory.Network
    SpanCategory.DATABASE -> JourneyCategory.Storage
    SpanCategory.UI_RENDER -> JourneyCategory.Render
    else -> JourneyCategory.Other
}

internal fun SpanCategory.toGlyph(): Glyph = when (this) {
    SpanCategory.UI_INTERACTION -> Glyph.Tap
    SpanCategory.VIEW_MODEL -> Glyph.Layers
    SpanCategory.USE_CASE -> Glyph.Flow
    SpanCategory.REPOSITORY, SpanCategory.DATABASE -> Glyph.Cylinder
    SpanCategory.NETWORK -> Glyph.Globe
    SpanCategory.BUSINESS_VALIDATION -> Glyph.Check
    SpanCategory.STATE_EMISSION -> Glyph.Bolt
    SpanCategory.UI_RENDER -> Glyph.Window
    else -> Glyph.Dot
}

internal fun SpanCategory.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

internal val BreakdownOrder: List<JourneyCategory> = JourneyCategory.entries
