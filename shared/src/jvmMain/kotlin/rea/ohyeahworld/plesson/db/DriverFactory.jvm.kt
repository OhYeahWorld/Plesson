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
    val url = "jdbc:sqlite:${dbFile.absolutePath}"
    val driver = JdbcSqliteDriver(url)
    // Идемпотентная доинициализация схемы: чинит старые файлы БД, в которых
    // нет новых таблиц (например, class_periods), и базы с оборванной
    // миграцией. См. SchemaRepair.kt.
    ensureDesktopSchema(driver, PlessonDatabase.Schema)
    return driver
}
