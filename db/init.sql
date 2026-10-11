-- ============================================================================
-- PLesson — физическая модель базы данных (Тема 1, Тема 3)
-- СУБД: PostgreSQL 15+
-- Инициализация: psql -U postgres -d plesson -f db/init.sql
-- ============================================================================

BEGIN;

DROP TABLE IF EXISTS lesson_department CASCADE;
DROP TABLE IF EXISTS lesson_teacher  CASCADE;
DROP TABLE IF EXISTS department      CASCADE;
DROP TABLE IF EXISTS teacher         CASCADE;
DROP TABLE IF EXISTS lesson          CASCADE;
DROP TABLE IF EXISTS "group"         CASCADE;
DROP TABLE IF EXISTS student         CASCADE;
DROP TABLE IF EXISTS teacher_department CASCADE;

-- ---------------------------------------------------------------------------
-- Справочник кафедр
-- ---------------------------------------------------------------------------
CREATE TABLE department (
    department_id   SERIAL PRIMARY KEY,
    name            VARCHAR(500) NOT NULL UNIQUE,          -- полное название кафедры
    short_name      VARCHAR(200),                          -- сокращённое название
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Справочник преподавателей
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION full_name_text(last VARCHAR, first VARCHAR, middle VARCHAR)
RETURNS TEXT LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT TRIM(CONCAT_WS(' ', last, first, middle)) $$;

CREATE TABLE teacher (
    teacher_id      BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(500) NOT NULL UNIQUE,          -- слаг из pleh.tech (кириллица)
    last_name       VARCHAR(100) NOT NULL,                 -- Фамилия
    first_name      VARCHAR(100) NOT NULL,                 -- Имя
    middle_name     VARCHAR(100),                          -- Отчество
    full_name       VARCHAR(300) GENERATED ALWAYS AS
                    (full_name_text(last_name, first_name, middle_name)) STORED,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- Связь M:M преподаватель <-> кафедра
CREATE TABLE teacher_department (
    teacher_id      BIGINT NOT NULL REFERENCES teacher(teacher_id)   ON DELETE CASCADE,
    department_id   INT    NOT NULL REFERENCES department(department_id) ON DELETE CASCADE,
    PRIMARY KEY (teacher_id, department_id)
);

-- ---------------------------------------------------------------------------
-- Учебные группы
-- ---------------------------------------------------------------------------
CREATE TABLE "group" (
    group_id        BIGSERIAL PRIMARY KEY,
    guid            UUID NOT NULL UNIQUE,                  -- group_guid из внешнего API
    name            VARCHAR(100) NOT NULL,                 -- напр. "15.27Д-ИВТ01/23б"
    faculty         VARCHAR(300),                          -- факультет / школа
    program_type    VARCHAR(100),                          -- Бакалавр / Магистр / Специалитет
    course          VARCHAR(50),                           -- "2-й курс"
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_group_name ON "group" (name);            -- ускорение поиска по названию

-- ---------------------------------------------------------------------------
-- Занятия (факт расписания)
-- ---------------------------------------------------------------------------
CREATE TABLE lesson (
    lesson_id       BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES "group"(group_id) ON DELETE CASCADE,
    day             DATE   NOT NULL,                       -- дата занятия
    period          SMALLINT NOT NULL CHECK (period BETWEEN 1 AND 8), -- номер пары
    discipline      VARCHAR(500) NOT NULL,                 -- предмет
    workload_type   VARCHAR(100),                          -- Лекция / Практика / Лаборатория...
    room            VARCHAR(50),                           -- аудитория
    building        VARCHAR(200),                          -- корпус
    campus          VARCHAR(200),                          -- кампус
    subgroup        VARCHAR(50),                           -- подгруппа (может не быть)
    approved        BOOLEAN NOT NULL DEFAULT TRUE,         -- подтверждено ли занятие
    teacher_names   TEXT,                                  -- список преподавателей через '; '
    source_key      VARCHAR(700) NOT NULL UNIQUE,          -- ключ синхронизации из источника
    synced_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_lesson_group_day ON lesson (group_id, day);   -- главный индекс выборки недели
CREATE INDEX idx_lesson_day       ON lesson (day);

-- Студент закреплён за группой (связь 1:M «группа — студент»)
CREATE TABLE student (
    student_id      BIGSERIAL PRIMARY KEY,
    group_id        BIGINT REFERENCES "group"(group_id) ON DELETE SET NULL,
    last_name       VARCHAR(100) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    email           VARCHAR(200) UNIQUE
);

-- ---------------------------------------------------------------------------
-- Хранимая процедура: расписание группы на неделю (аналог rpc/group_week)
-- Использует транзакционное чтение и индексы.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION group_week(
    p_group_guid UUID,
    p_from       DATE,
    p_to         DATE
)
RETURNS TABLE (
    day           DATE,
    period        SMALLINT,
    discipline    VARCHAR(500),
    workload_type VARCHAR(100),
    room          VARCHAR(50),
    building      VARCHAR(200),
    campus        VARCHAR(200),
    teacher_names TEXT,
    subgroup      VARCHAR(50),
    approved      BOOLEAN
)
LANGUAGE sql STABLE AS $$
    SELECT l.day, l.period, l.discipline, l.workload_type, l.room,
           l.building, l.campus, l.teacher_names, l.subgroup, l.approved
    FROM lesson l
    JOIN "group" g ON g.group_id = l.group_id
    WHERE g.guid = p_group_guid
      AND l.day BETWEEN p_from AND p_to
    ORDER BY l.day ASC, l.period ASC;
$$;

COMMIT;
