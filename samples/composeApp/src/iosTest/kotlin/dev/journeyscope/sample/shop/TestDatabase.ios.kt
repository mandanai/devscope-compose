package dev.journeyscope.sample.shop

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import dev.journeyscope.sample.shop.db.CartDatabase

actual fun createTestDriver(): SqlDriver = inMemoryDriver(CartDatabase.Schema)
