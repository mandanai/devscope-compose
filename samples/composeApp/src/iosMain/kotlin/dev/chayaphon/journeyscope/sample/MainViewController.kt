package dev.chayaphon.journeyscope.sample

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.chayaphon.journeyscope.export.share.IosShareSink
import dev.chayaphon.journeyscope.export.storage.iosTraceDirectory
import dev.chayaphon.journeyscope.sample.shop.SampleExport
import dev.chayaphon.journeyscope.sample.shop.SampleGraph
import dev.chayaphon.journeyscope.sample.shop.db.CartDatabase
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    SampleGraph.configure(NativeSqliteDriver(CartDatabase.Schema, "cart.db"))
    SampleExport.install(iosTraceDirectory(), IosShareSink())
    return ComposeUIViewController { App() }
}
