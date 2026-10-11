package dev.chayaphon.journeyscope.sample

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.chayaphon.journeyscope.export.share.AndroidShareSink
import dev.chayaphon.journeyscope.export.storage.androidTraceDirectory
import dev.chayaphon.journeyscope.sample.shop.SampleExport
import dev.chayaphon.journeyscope.sample.shop.SampleGraph
import dev.chayaphon.journeyscope.sample.shop.db.CartDatabase

fun installSampleDatabase(context: Context) {
    SampleGraph.configure(AndroidSqliteDriver(CartDatabase.Schema, context.applicationContext, "cart.db"))
    SampleExport.install(androidTraceDirectory(context), AndroidShareSink(context.applicationContext))
}
