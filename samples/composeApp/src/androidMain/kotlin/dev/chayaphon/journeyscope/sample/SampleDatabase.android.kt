package dev.chayaphon.journeyscope.sample

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.chayaphon.journeyscope.sample.shop.SampleGraph
import dev.chayaphon.journeyscope.sample.shop.db.CartDatabase

fun installSampleDatabase(context: Context) {
    SampleGraph.configure(AndroidSqliteDriver(CartDatabase.Schema, context.applicationContext, "cart.db"))
}
