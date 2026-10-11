package dev.chayaphon.journeyscope.flow

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.emptyFlow

public fun LifecycleOwner.asLifecycleSource(): LifecycleSource = LifecycleSource { emptyFlow() }
