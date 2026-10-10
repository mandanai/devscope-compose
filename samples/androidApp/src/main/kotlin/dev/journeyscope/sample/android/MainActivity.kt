package dev.journeyscope.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import dev.journeyscope.flow.asLifecycleSource
import dev.journeyscope.sample.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(lifecycleSource = asLifecycleSource()) }
    }
}
