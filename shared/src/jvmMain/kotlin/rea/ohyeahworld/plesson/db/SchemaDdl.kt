package rea.ohyeahworld.plesson.db

/**
 * Полный идемпотентный набор DDL текущей схемы (соответствует миграциям
 * 0001.sqm / 0002.sqm, версия схемы — PlessonDatabase.Schema.version).
 *
 * Используется только для «ремонта» старых файлов ~/.plesson/plesson.db,
 * в которых отсутствуют таблицы, добавленные позже (class_periods, meta),
 * либо где инициализация была оборвана на середине.
 *
 * ВАЖНО: все объекты создаются через IF NOT EXISTS, поэтому список безопасен
 * для баз с уже существующими таблицами и данными. При изменении схемы
 * (.sq/.sqm) этот список нужно синхронизировать с ней.
 */
internal val SCHEMA_REPAIR_DDL: List<String> = listOf(
    """
    CREATE TABLE IF NOT EXISTS groups (
        group_guid   TEXT PRIMARY KEY NOT NULL,
        group_name   TEXT NOT NULL,
        faculty      TEXT,
        program_type TEXT,
        course       TEXT
    )
    """.trimIndent(),

    """
    CREATE TABLE IF NOT EXISTS teachers (
        teacher_slug TEXT PRIMARY KEY NOT NULL,
        teacher_name TEXT NOT NULL
    )
    """.trimIndent(),

    """
    CREATE TABLE IF NOT EXISTS teacher_departments (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        slug       TEXT NOT NULL REFERENCES teachers(teacher_slug) ON DELETE CASCADE,
        department TEXT NOT NULL
    )
    """.trimIndent(),

    """
    CREATE TABLE IF NOT EXISTS lessons (
        id            INTEGER PRIMARY KEY AUTOINCREMENT,
        group_guid    TEXT NOT NULL REFERENCES groups(group_guid) ON DELETE CASCADE,
        day           TEXT NOT NULL,
        period        INTEGER NOT NULL,
        discipline    TEXT NOT NULL,
        workload_type TEXT,
        room          TEXT,
        building      TEXT,
        campus        TEXT,
        subgroup      TEXT,
        approved      INTEGER NOT NULL DEFAULT 1,
        synced_at     TEXT NOT NULL
    )
    """.trimIndent(),

    """
    CREATE TABLE IF NOT EXISTS lesson_teachers (
        lesson_id    INTEGER NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
        teacher_slug TEXT NOT NULL REFERENCES teachers(teacher_slug) ON DELETE CASCADE,
        PRIMARY KEY (lesson_id, teacher_slug)
    )
    """.trimIndent(),

    // Справочник времени пар и ключ-значение справочник — именно их не хватает
    // в старых файлах БД (краш "no such table: class_periods").
    """
    CREATE TABLE IF NOT EXISTS class_periods (
        period_number INTEGER PRIMARY KEY NOT NULL,
        start_time    TEXT NOT NULL,
        end_time      TEXT NOT NULL,
        synced_at     TEXT NOT NULL
    )
    """.trimIndent(),

    """
    CREATE TABLE IF NOT EXISTS meta (
        key   TEXT PRIMARY KEY NOT NULL,
        value TEXT NOT NULL
    )
    """.trimIndent(),

    "CREATE INDEX IF NOT EXISTS idx_lessons_group_day ON lessons(group_guid, day)",
    "CREATE INDEX IF NOT EXISTS idx_lessons_day ON lessons(day)",
    "CREATE INDEX IF NOT EXISTS idx_groups_name ON groups(group_name)",
    "CREATE INDEX IF NOT EXISTS idx_teachers_name ON teachers(teacher_name)",
    "CREATE INDEX IF NOT EXISTS idx_dept_slug ON teacher_departments(slug)",
    "CREATE INDEX IF NOT EXISTS idx_lt_teacher ON lesson_teachers(teacher_slug)",
)
