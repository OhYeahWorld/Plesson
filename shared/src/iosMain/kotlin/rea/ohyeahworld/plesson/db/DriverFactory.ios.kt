package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import rea.ohyeahworld.plesson.PlessonDatabase

/** iOS: нативный SQLite (CoreData-free), файл plesson.db в песочнице приложения. */
actual fun createSqlDriver(): SqlDriver =
    NativeSqliteDriver(PlessonDatabase.Schema, "plesson.db")
