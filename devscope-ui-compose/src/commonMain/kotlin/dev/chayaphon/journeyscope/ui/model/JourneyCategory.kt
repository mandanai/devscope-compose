package dev.chayaphon.journeyscope.ui.model

import dev.chayaphon.journeyscope.core.model.SpanCategory

/** Coarse groups used to color and total span time in the inspector. */
public enum class JourneyCategory(
    public val label: String,
) {
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
    Route,
    Back,
    Close,
    ChevronDown,
}

internal fun SpanCategory.toJourneyCategory(): JourneyCategory =
    when (this) {
        SpanCategory.UI_INTERACTION,
        SpanCategory.VIEW_MODEL,
        SpanCategory.USE_CASE,
        SpanCategory.REPOSITORY,
        SpanCategory.BUSINESS_VALIDATION,
        SpanCategory.STATE_EMISSION,
        SpanCategory.STATE,
        SpanCategory.LIFECYCLE,
        SpanCategory.NAVIGATION,
        -> JourneyCategory.UiVm

        SpanCategory.NETWORK -> JourneyCategory.Network

        SpanCategory.DATABASE -> JourneyCategory.Storage

        SpanCategory.UI_RENDER -> JourneyCategory.Render

        else -> JourneyCategory.Other
    }

internal fun SpanCategory.toGlyph(): Glyph =
    when (this) {
        SpanCategory.UI_INTERACTION -> Glyph.Tap
        SpanCategory.VIEW_MODEL -> Glyph.Layers
        SpanCategory.USE_CASE -> Glyph.Flow
        SpanCategory.REPOSITORY, SpanCategory.DATABASE -> Glyph.Cylinder
        SpanCategory.NETWORK -> Glyph.Globe
        SpanCategory.BUSINESS_VALIDATION -> Glyph.Check
        SpanCategory.STATE_EMISSION, SpanCategory.STATE -> Glyph.Bolt
        SpanCategory.LIFECYCLE -> Glyph.Layers
        SpanCategory.NAVIGATION -> Glyph.Route
        SpanCategory.UI_RENDER -> Glyph.Window
        else -> Glyph.Dot
    }

internal fun SpanCategory.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

internal val BreakdownOrder: List<JourneyCategory> = JourneyCategory.entries
