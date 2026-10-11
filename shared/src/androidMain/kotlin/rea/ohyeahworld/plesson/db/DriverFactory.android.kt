package rea.ohyeahworld.plesson.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import rea.ohyeahworld.plesson.PlessonDatabase

/** Android: нативный SQLite через AndroidSqliteDriver. */
actual fun createSqlDriver(): SqlDriver =
    throw UnsupportedOperationException(
        "On Android call createSqlDriver(context) from MainActivity/Application."
    )

fun createSqlDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(PlessonDatabase.Schema, context.applicationContext, "plesson.db")
