package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Web (Kotlin/JS): локальная БД в браузере не поддерживается — приложение
 * работает через Android/iOS/Desktop-сборки. UI корректно показывает сообщение.
 */
actual fun createSqlDriver(): SqlDriver =
    throw UnsupportedOperationException("SQLite driver is not available on Kotlin/JS web target")
