package dev.chayaphon.journeyscope.sample.shop

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.chayaphon.journeyscope.sample.shop.db.CartDatabase

actual fun createTestDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CartDatabase.Schema.create(it) }
