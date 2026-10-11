package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import rea.ohyeahworld.plesson.PlessonDatabase

/**
 * Идемпотентная доинициализация схемы SQLite поверх файла ~/.plesson/plesson.db.
 *
 * Зачем это нужно: файлы БД, созданные старыми версиями приложения, не имели
 * номера схемы (PRAGMA user_version = 0) и новых таблиц (class_periods, meta).
 * Штатный путь SQLDelight (create/migrate) на таких файлах либо не запускался,
 * либо падал на "table already exists" при оборванной ранее инициализации —
 * из-за чего приложение потом крашилось на
 * "[SQLITE_ERROR] no such table: class_periods".
 *
 * Решение простое и надёжное:
 *  - читаем user_version напрямую через штатные запросы драйвера;
 *  - если версия актуальна — ничего не делаем;
 *  - иначе выполняем полный набор DDL со всеми "IF NOT EXISTS"
 *    (см. SchemaDdl.kt) и проставляем актуальный user_version.
 * Существующие таблицы и данные не затрагиваются. Ошибки по отдельным
 * объектам молча пропускаются — они не мешают основному ремонту.
 */
internal fun ensureDesktopSchema(driver: SqlDriver) {
    try {
        if (driver.userVersion() >= PlessonDatabase.Schema.version) return // схема уже актуальна
        repairMissingObjects(driver)
        driver.setUserVersion(PlessonDatabase.Schema.version)
    } catch (_: Exception) {
        // Ремонт — best-effort: если не удалось, штатный путь create()/миграций
        // всё равно попытается инициализировать схему.
    }
}

private fun repairMissingObjects(driver: SqlDriver) {
    for (sql in SCHEMA_REPAIR_DDL) {
        try {
            driver.execute(null, sql, 0)
        } catch (_: Exception) {
            // объект уже существует или неприменим к текущему состоянию базы — пропускаем
        }
    }
}

/** PRAGMA user_version как число через штатный executeQuery драйвера. */
private fun SqlDriver.userVersion(): Long =
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0,
    ).value

private fun SqlDriver.setUserVersion(version: Long) {
    // PRAGMA не принимает плейсхолдеры — значение long безопасно инлайним.
    execute(null, "PRAGMA user_version = $version", 0)
}

/** Есть ли в файле БД пользовательские таблицы (файл открыт/создан драйвером). */
internal fun SqlDriver.fileHasTables(): Boolean =
    executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) (cursor.getLong(0) ?: 0L) > 0L else false)
        },
        parameters = 0,
    ).value

/** Номер схемы из PRAGMA user_version (0 у старых файлов без метаданных). */
internal fun SqlDriver.schemaVersion(): Long = userVersion()
