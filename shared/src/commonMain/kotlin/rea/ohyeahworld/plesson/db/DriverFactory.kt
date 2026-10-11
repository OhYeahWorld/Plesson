package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Платформенные драйверы SQLite (Тема 3.1: физическая модель БД на каждой платформе).
 */
expect fun createSqlDriver(): SqlDriver
