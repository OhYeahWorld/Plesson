package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import rea.ohyeahworld.plesson.PlessonDatabase
import java.io.File

/**
 * Desktop (JVM): обычный файл SQLite в ~/.plesson/plesson.db.
 * Максимально стабильный вариант — никаких серверов и портов.
 */
actual fun createSqlDriver(): SqlDriver {
    val dir = File(System.getProperty("user.home"), ".plesson")
    dir.mkdirs()
    val dbFile = File(dir, "plesson.db")
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (!PlessonDatabase.Schema.validate(driver)) {
        PlessonDatabase.Schema.create(driver)
    }
    return driver
}
