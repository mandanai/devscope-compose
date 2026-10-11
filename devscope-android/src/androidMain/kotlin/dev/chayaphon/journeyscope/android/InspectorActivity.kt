package dev.chayaphon.journeyscope.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.ui.JourneyScopeInspector
import dev.chayaphon.journeyscope.ui.state.asTraceSource

internal class InspectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = JourneyScopeAndroid.exportController ?: DefaultExport.controller(applicationContext)
        setContent {
            val tracer by JourneyScope.tracerFlow.collectAsState()
            JourneyScopeInspector(
                source = tracer.recorder.asTraceSource(),
                onClose = ::finish,
                exportController = controller,
            )
        }
    }
}
