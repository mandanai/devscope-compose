package dev.journeyscope.sample

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.journeyscope.sample.shop.SampleGraph
import dev.journeyscope.sample.shop.db.CartDatabase
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    SampleGraph.configure(NativeSqliteDriver(CartDatabase.Schema, "cart.db"))
    return ComposeUIViewController { App() }
}
