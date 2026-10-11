package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.Connection

/**
 * Надёжная инициализация схемы поверх JdbcSqliteDriver (десктоп).
 *
 * Почему нельзя полагаться только на PlessonDatabase.Schema.create(driver):
 * 1) Файлы БД, созданные старыми версиями приложения, вообще не имели номера
 *    схемы (PRAGMA user_version = 0), и такие файлы раньше открывались без
 *    миграций — из-за этого syncClassPeriods падал с
 *    "[SQLITE_ERROR] no such table: class_periods".
 * 2) Если процесс был убит посреди create()/миграции (например, hot reload во
 *    время записи), в файле остаются отдельные таблицы, а user_version = 0.
 *    Стандартный create() считает такую базу "новой", пытается создать таблицы
 *    заново, падает на "table already exists" — и если ошибка проглатывается,
 *    приложение потом снова крашится на нехватке таблиц.
 *
 * Логика:
 *  - читаем user_version напрямую; если он актуален — ничего не делаем;
 *  - полностью пустая база — создаём схему штатным create();
 *  - в базе есть таблицы, но версия не проставлена (или отстает) —
 *    идемпотентно достраиваем недостающие объекты (CREATE ... IF NOT EXISTS)
 *    и проставляем актуальную версию. Данные при этом не трогаются.
 */
internal fun ensureDesktopSchema(driver: SqlDriver, schema: SqlSchema<QueryResult.Value<Unit>>) {
    val jdbc = driver as? JdbcSqliteDriver ?: return
    val conn = jdbc.connectionOrNull() ?: return

    if (conn.userVersion() >= schema.version) return // схема уже актуальна

    if (!conn.hasUserObjects()) {
        // Новая (или полностью пустая) база: штатное создание + простановка версии.
        try {
            schema.create(driver)
        } catch (_: Exception) {
            // create() мог оборваться на середине — починим ниже точечно.
        }
        repairMissingObjects(conn, schema)
        conn.setUserVersion(schema.version)
        return
    }

    // Существующая база без корректной версии (старый файл или оборванная
    // инициализация): достраиваем недостающее и проставляем версию.
    repairMissingObjects(conn, schema)
    conn.setUserVersion(schema.version)
}

private fun repairMissingObjects(conn: Connection, schema: SqlSchema<QueryResult.Value<Unit>>) {
    for (sql in collectCreateStatements(schema)) {
        try {
            conn.exec(sql)
        } catch (_: Exception) {
            // обрывок DDL из прерванной транзакции / объект уже существует — пропускаем
        }
    }
}

/**
 * Достаёт шаги создания объектов из сгенерированного Schema, «выполняя»
 * миграции на драйвере-ловушке: это исключает ручное дублирование DDL.
 * Дополнительно вызываем create() на ловушке — для баз, где какие-то таблицы
 * остались от промежуточных версий схемы.
 */
private fun collectCreateStatements(schema: SqlSchema<QueryResult.Value<Unit>>): List<String> {
    val stmts = mutableListOf<String>()
    // Драйвер-ловушка: записываем все DDL, которые сгенерированная схема
    // пытается выполнить при create()/migrate().
    val trap = RecordingDriver(stmts)
    try {
        schema.migrate(trap, oldVersion = 1, newVersion = schema.version)
    } catch (_: Exception) {
    }
    try {
        schema.create(trap)
    } catch (_: Exception) {
    }
    // CREATE TABLE без IF NOT EXISTS превращаем в идемпотентный вариант;
    // индексы тоже выполняем через IF NOT EXISTS.
    return stmts.filter { it.startsWith("CREATE", ignoreCase = true) }.map { sql ->
        val kw = Regex("""^CREATE\s+(UNIQUE\s+|VIRTUAL\s+)?(TABLE|INDEX)\b""", RegexOption.IGNORE_CASE)
        kw.find(sql)?.let { m ->
            if (!sql.contains("IF NOT EXISTS", ignoreCase = true)) {
                sql.removeRange(m.range).let { rest ->
                    "CREATE ${m.groupValues[1].ifBlank { "" }}${m.groupValues[2]} IF NOT EXISTS $rest"
                        .replace(Regex("\\s+"), " ")
                }
            } else sql
        } ?: sql
    }.distinct()
}

// ---- helpers поверх JDBC ----

/** JdbcSqliteDriver хранит соединение внутри ConnectionManager; достаём его рефлексивно. */
private fun JdbcSqliteDriver.connectionOrNull(): Connection? = try {
    val mgrField = javaClass.superclass.getDeclaredField("connectionManager")
    mgrField.isAccessible = true
    val manager = mgrField.get(this) ?: return null
    val connField = manager.javaClass.getDeclaredField("connection")
    connField.isAccessible = true
    connField.get(manager) as? Connection
} catch (_: Exception) {
    null
}

private fun Connection.userVersion(): Long =
    createStatement().use { st ->
        st.executeQuery("PRAGMA user_version").use { rs ->
            if (rs.next()) rs.getLong(1) else 0L
        }
    }

private fun Connection.setUserVersion(version: Long) {
    // PRAGMA не принимает плейсхолдеры — значение long безопасно инлайним.
    createStatement().use { it.execute("PRAGMA user_version = $version") }
}

private fun Connection.hasUserObjects(): Boolean =
    createStatement().use { st ->
        st.executeQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type IN ('table','index') " +
                "AND name NOT LIKE 'sqlite_%'"
        ).use { rs -> rs.next() && rs.getInt(1) > 0 }
    }

private fun Connection.exec(sql: String) {
    createStatement().use { it.execute(sql) }
}

/**
 * SqlDriver, который не выполняет SQL, а записывает его в список.
 * Реализованы только методы, реально нужные сгенерированной схеме
 * (execute + currentTransaction).
 */
private class RecordingDriver(private val sink: MutableList<String>) : SqlDriver {
    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        sink += sql.trim()
        return QueryResult.Value(0L)
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = throw UnsupportedOperationException("RecordingDriver")

    override fun currentTransaction(): Transacter.Transaction? = null
}
