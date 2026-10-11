package dev.chayaphon.journeyscope.sample

import androidx.compose.runtime.Composable

/** Debug tooling the host can offer; null means the sample shows no inspector entry points. */
interface SampleInspector {
    @Composable
    fun Live(onClose: () -> Unit)

    @Composable
    fun Demo(onClose: () -> Unit)
}
