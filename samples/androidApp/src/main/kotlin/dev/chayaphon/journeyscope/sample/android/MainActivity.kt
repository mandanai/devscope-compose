package dev.chayaphon.journeyscope.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chayaphon.journeyscope.flow.asLifecycleSource
import dev.chayaphon.journeyscope.sample.App
import dev.chayaphon.journeyscope.sample.installSampleDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        installSampleDatabase(this)
        setContent { App(lifecycleSource = asLifecycleSource()) }
    }
}
