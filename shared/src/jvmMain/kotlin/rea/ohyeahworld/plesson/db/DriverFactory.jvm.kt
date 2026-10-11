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
    // Всегда открываем драйвер и вызываем create(): SQLDelight сам определит
    // версию файла — для новой БД создаст полную схему, для существующей
    // прогонит недостающие миграции (.sqm). Это чинит старые файлы,
    // в которых нет новых таблиц (например, class_periods).
    return JdbcSqliteDriver(url).also { driver ->
        runCatching { PlessonDatabase.Schema.create(driver) }
    }
}
