package dev.chayaphon.journeyscope.sample.shop

import dev.chayaphon.journeyscope.core.expectation.ExpectedSequence
import dev.chayaphon.journeyscope.core.expectation.ExpectedStep
import dev.chayaphon.journeyscope.core.model.SpanCategory

/** Applies only to Add to Cart journeys; other journeys are never judged against it. */
val AddToCartCompletion =
    ExpectedSequence(
        name = "Add to Cart completion",
        steps =
            listOf(
                ExpectedStep.category("Network request", SpanCategory.NETWORK),
                ExpectedStep.category("Database write", SpanCategory.DATABASE, "cart.upsert"),
                ExpectedStep.category("UI render checkpoint", SpanCategory.UI_RENDER),
            ),
        appliesTo = { it.name == "Add to Cart" },
    )
