package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.sql.wasm.SqlWasmConnection
import app.cash.sqldelight.driver.sql.wasm.SqlWasmDriver
import app.cash.sqldelight.driver.sql.wasm.SqlWasmPreparedStatement
import app.cash.sqldelight.driver.sql.wasm.SqlWasmResult
import kotlinx.coroutines.await
import org.kh.webql.database.IDBDatabase
import org.kh.webql.database.IDBTransaction
import org.kh.webql.database.WebQL
import kotlin.js.Promise

/**
 * Web (Kotlin/Wasm): SQLite поверх Web SQL API браузера (SqlDelight sql-wasm driver).
 * Данные живут в хранилище браузера — приложение работает офлайн после синхронизации.
 */
actual fun createSqlDriver(): SqlDriver =
    throw UnsupportedOperationException("Web: use suspend createWebSqlDriver()")

private class WasmWebDb(private val db: IDBDatabase) : SqlWasmConnection {
    override fun prepare(sql: String): SqlWasmPreparedStatement =
        object : SqlWasmPreparedStatement {
            override fun bindString(index: Int, value: String?) {
                // Web SQL подставляет параметры позиционно ([?]); сохраняем порядок.
                if (value != null) params.add(value) else params.addNull()
            }

            override fun executeQuery(): SqlWasmResult {
                val rows = mutableListOf<List<String?>>()
                var ok = true
                db.transaction { tx: IDBTransaction ->
                    tx.executeSql(sql, toJsArray(params)) { _, cursor ->
                        outer@ for (i in 0 until cursor.length()) {
                            val row = cursor.item(i).unsafeCast<dynamic>()
                            val cols = mutableListOf<String?>()
                            val names = columnNamesOf(row)
                            for (n in names) cols.add(row[n] as? String)
                            rows.add(cols)
                        }
                    }
                }.await()
                return object : SqlWasmResult {
                    override fun next(): Boolean = false // заполняется ниже через all-подход
                    override fun getString(index: Int): String? = null
                    override fun getLong(index: Int): Long? = null
                    override fun getBytes(index: Int): ByteArray? = null
                    override fun getDouble(index: Int): Double? = null
                }
            }

            private val params = JsParamList()
        }

    override fun clearAllTables() {}
}

private external interface JsAny

// Минимальные external-обёртки над Web SQL API (window.openDatabase),
// чтобы не зависеть от экспериментальных пакетов.
@JsFun("(name) => window.openDatabase(name, '1.0', name, 5 * 1024 * 1024)")
private external fun jsOpenDatabase(name: String): dynamic

@JsFun("(db, sql, args) => new Promise((res, rej) => db.transaction(t => t.executeSql(sql, args, (tx, r) => { const out = []; for (let i = 0; i < r.rows.length; i++) { const row = r.rows.item(i); const keys = Object.keys(row); out.push(keys.map(k => row[k])); } res({rows: out, columns: out[0] ? Object.keys(r.rows.item(0)) : []}); }, (e, err) => rej(String(err && err.message ? err.message : err))))))")
private external fun jsExec(db: dynamic, sql: String, args: Array<String?>): dynamic

@JsFun("(db, sql, args) => new Promise((res, rej) => db.transaction(t => t.executeSql(sql, args, () => res(), (e, err) => rej(String(err && err.message ? err.message : err))))))")
private external fun jsExecVoid(db: dynamic, sql: String, args: Array<String?>): dynamic

private class WasmWebSqlDriver(private val db: dynamic) : SqlWasmConnection {
    override fun prepare(sql: String): SqlWasmPreparedStatement = WasmStatement(db, sql)

    override fun clearAllTables() {
        listOf("lesson_teachers", "teacher_departments", "lessons", "teachers", "groups").forEach {
            try {
                jsExecVoid(db, "DELETE FROM $it", emptyArray()).await()
            } catch (_: Throwable) {
            }
        }
    }
}

private class WasmStatement(private val db: dynamic, private val sql: String) : SqlWasmPreparedStatement {
    private val params = mutableListOf<String?>()

    override fun bindString(index: Int, value: String?) {
        while (params.size < index) params.add(null)
        if (index <= params.size) params[index - 1] = value else params.add(value)
    }

    override fun executeQuery(): SqlWasmResult {
        val promise = jsExec(db, sql, params.toTypedArray())
        val res = promise.await0()
        val rows = res.rows.unsafeCast<Array<Array<Any?>>>()
        return object : SqlWasmResult {
            private var cursor = -1
            private var current: Array<Any?>? = null
            override fun next(): Boolean {
                cursor++
                current = rows.getOrNull(cursor)
                return current != null
            }

            override fun getString(index: Int): String? = current?.getOrNull(index)?.toString()
            override fun getLong(index: Int): Long? = current?.getOrNull(index)?.toString()?.toDoubleOrNull()?.toLong()
            override fun getBytes(index: Int): ByteArray? = getString(index)?.encodeToByteArray()
            override fun getDouble(index: Int): Double? = current?.getOrNull(index)?.toString()?.toDoubleOrNull()
        }
    }
}

private fun dynamic.await0(): dynamic = kotlin.coroutines.suspendCoroutine { cont ->
    this.then { v: dynamic -> cont.resumeWith(Result.success(v)); Unit }
        .catch { e: dynamic -> cont.resumeWith(Result.failure(RuntimeException(e.toString()))); Unit }
}

suspend fun createWebSqlDriver(): SqlDriver {
    val db = jsOpenDatabase("plesson.db")
    return SqlWasmDriver(WasmWebSqlDriver(db), "plesson")
}
