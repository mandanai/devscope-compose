package dev.journeyscope.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlin.random.Random

public fun interface IdGenerator {
    public fun nextId(): String
}

/** Random per-instance prefix keeps ids unique across sessions once traces are persisted or exported. */
public class RandomPrefixIdGenerator(
    private val prefix: String = Random.nextLong().toULong().toString(HEX_RADIX),
) : IdGenerator {
    private val counter = MutableStateFlow(0L)

    override fun nextId(): String = "$prefix-${counter.updateAndGet { it + 1 }.toString(HEX_RADIX)}"

    private companion object {
        const val HEX_RADIX = 16
    }
}
