package dev.chayaphon.journeyscope.sample.shop

import app.cash.sqldelight.db.SqlDriver
import dev.chayaphon.journeyscope.sample.shop.db.CartDatabase

expect fun createTestDriver(): SqlDriver

fun createTestDatabase(): CartDatabase = CartDatabase(createTestDriver())
