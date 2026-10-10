package dev.journeyscope.flow

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.map

/** Must be collected on the main thread, as required by [Lifecycle.currentStateFlow]. */
public fun LifecycleOwner.asLifecycleSource(): LifecycleSource =
    LifecycleSource {
        lifecycle.currentStateFlow.map { it.toCollectorState() }
    }

private fun Lifecycle.State.toCollectorState(): CollectorLifecycleState =
    when (this) {
        Lifecycle.State.INITIALIZED -> CollectorLifecycleState.Created
        Lifecycle.State.CREATED -> CollectorLifecycleState.Created
        Lifecycle.State.STARTED -> CollectorLifecycleState.Started
        Lifecycle.State.RESUMED -> CollectorLifecycleState.Resumed
        Lifecycle.State.DESTROYED -> CollectorLifecycleState.Destroyed
    }
