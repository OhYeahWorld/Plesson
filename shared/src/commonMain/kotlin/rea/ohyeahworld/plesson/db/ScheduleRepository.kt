package rea.ohyeahworld.plesson.db

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import rea.ohyeahworld.plesson.PlessonDatabase
import rea.ohyeahworld.plesson.api.RestClient
import rea.ohyeahworld.plesson.api.entity.Group
import rea.ohyeahworld.plesson.api.entity.Lesson
import rea.ohyeahworld.plesson.api.Semester
import rea.ohyeahworld.plesson.api.entity.WeekLesson
import rea.ohyeahworld.plesson.api.sanitizeLikeParam
import rea.ohyeahworld.plesson.shortTime

/**
 * Data Access Layer (Тема 3.1): единственный источник данных для UI — локальная SQLite.
 * При первом успешном подключении расписание семестра сохраняется в базу,
 * и дальше приложение работает офлайн с кэшированными данными.
 */
class ScheduleRepository(driver: SqlDriver) {

    private val db = PlessonDatabase(driver)
    // SQLDelight генерирует по одному классу запросов на .sq-файл
    private val groupsQ = db.groupsQueries
    private val lessonsQ = db.lessonsQueries
    private val teachersQ = db.teachersQueries
    private val periodsQ = db.classPeriodsQueries
    private val metaQ = db.metaQueries

    /** Понедельник недели, содержащей [date]. */
    fun weekMonday(date: LocalDate): LocalDate =
        date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))

    fun weekSunday(date: LocalDate): LocalDate = weekMonday(date).plus(DatePeriod(days = 6))

    // ---------------------------------------------------------------- Groups

    suspend fun loadGroups(): List<Group> = withContext(Dispatchers.Default) {
        groupsQ.selectAllGroups().executeAsList().map { it.toEntity() }
    }

    suspend fun searchGroups(query: String): List<Group> = withContext(Dispatchers.Default) {
        val q = sanitizeLikeParam(query)
        if (q.isEmpty()) groupsQ.selectAllGroups().executeAsList().map { it.toEntity() }
        else groupsQ.searchGroups(q).executeAsList().map { it.toEntity() }
    }

    suspend fun upsertGroup(group: Group) = withContext(Dispatchers.Default) {
        val guid = group.group_guid ?: return@withContext
        groupsQ.upsertGroup(
            guid = guid,
            name = group.group_name ?: guid,
            faculty = group.faculty,
            program_type = group.program_type,
            course = group.course,
        )
    }

    /** Синхронизация справочника групп: API → SQLite. Возвращает число сохранённых групп. */
    suspend fun syncGroups(): Int = syncGroups(null)

    /**
     * Синхронизация групп: из API (remoteNames == null) или из локального кэша ответа
     * (remoteNames != null — сеть недоступна, используем ранее полученные данные).
     */
    suspend fun syncGroups(remoteNames: List<String>?): Int = withContext(Dispatchers.Default) {
        val remote: List<Group> = if (remoteNames != null) {
            remoteNames.map { Group(group_guid = it, group_name = it) }
        } else {
            runCatching { restClient.getGroups() }.getOrNull() ?: return@withContext 0
        }
        db.transaction {
            for (g in remote) {
                val guid = g.group_guid ?: continue
                groupsQ.upsertGroup(
                    guid = guid,
                    name = g.group_name ?: guid,
                    faculty = g.faculty,
                    program_type = g.program_type,
                    course = g.course,
                )
            }
        }
        if (remoteNames == null) {
            metaQ.setMeta(GROUPS_CACHE_KEY, remote.mapNotNull { it.group_name }.joinToString("\n"))
        }
        remote.size
    }

    /** Поиск групп через API (ilike на сервере); при успехе обновляет офлайн-кэш названий. */
    suspend fun searchGroupsRemote(query: String): List<Group> = withContext(Dispatchers.Default) {
        val remote = restClient.getGroups(query)
        if (remote.isNotEmpty()) {
            db.transaction {
                for (g in remote) {
                    val guid = g.group_guid ?: continue
                    groupsQ.upsertGroup(guid, g.group_name ?: guid, g.faculty, g.program_type, g.course)
                }
            }
        }
        remote
    }

    /** Восстановление списка групп из офлайн-кэша (используется, когда справочник в БД пуст). */
    suspend fun restoreGroupsFromCache(): List<Group> = withContext(Dispatchers.Default) {
        val names = cachedGroupNames()
        db.transaction {
            for (n in names) groupsQ.upsertGroup(guid = n, name = n, faculty = null, program_type = null, course = null)
        }
        names.map { Group(group_guid = it, group_name = it) }
    }

    /** Восстановление расписания группы из снимка (таблица meta) после потери сети на пустой БД. */
    suspend fun restoreFromSnapshot(groupGuid: String, items: List<WeekLesson>, today: LocalDate) =
        withContext(Dispatchers.Default) {
            val (semesterStart, semesterEnd) = Semester.bounds(today)
            writeRange(groupGuid, semesterStart.toString(), semesterEnd.toString(), items)
        }

    // -------------------------------------------------------------- Lessons

    /** Расписание на неделю из БД (UI читает только отсюда — работает офлайн). */
    suspend fun lessonsForWeek(groupGuid: String, anyDateOfTheWeek: LocalDate): List<Lesson> =
        withContext(Dispatchers.Default) {
            val from = weekMonday(anyDateOfTheWeek).toString()
            val to = weekSunday(anyDateOfTheWeek).toString()

            // 1) сами занятия (строка сгенерирована из `SELECT l.*, ... AS teacher_name`)
            val rows = lessonsQ.selectByGroupAndRange(groupGuid, from, to).executeAsList()

            // 2) все преподаватели каждого занятия (связь M:M через lesson_teachers)
            val teacherNamesByLesson: Map<Long, List<String>> =
                teachersQ.teachersForLessons(groupGuid, from, to)
                    .executeAsList()
                    .groupBy({ row -> row.lesson_id }, { row -> row.teacher_name })

            // 3) реальное время пар из локального справочника class_periods
            val periods = periodsQ.selectAllPeriods().executeAsList()
                .associateBy { it.period_number.toInt() }

            rows.map { row ->
                val period = row.period.toInt()
                val p = periods[period]
                Lesson(
                    id = row.id,
                    day = row.day,
                    period = row.period.toString(),
                    discipline = row.discipline,
                    workload_type = row.workload_type,
                    room = row.room,
                    building = row.building,
                    campus = row.campus,
                    teacher_name = teacherNamesByLesson[row.id]?.firstOrNull() ?: row.teacher_name,
                    teacher_names = teacherNamesByLesson[row.id] ?: listOfNotNull(row.teacher_name),
                    subgroup = row.subgroup,
                    approved = row.approved != 0L,
                    startTime = shortTime(p?.start_time),
                    endTime = shortTime(p?.end_time),
                )
            }
        }

    /**
     * Синхронизация расписания группы на весь семестр: API → транзакция → SQLite.
     * Возвращает true, если данные получены из сети и сохранены; false — сеть недоступна
     * (в этом случае в базе остаётся ранее сохранённое расписание).
     */
    suspend fun syncSemester(groupGuid: String, today: LocalDate): Boolean =
        withContext(Dispatchers.Default) {
            val (semesterStart, semesterEnd) = Semester.bounds(today)
            val remote: List<WeekLesson>? = runCatching {
                restClient.getSchedule(groupGuid, semesterStart.toString(), semesterEnd.toString())
            }.getOrNull()?.takeIf { it.isNotEmpty() }

            if (remote == null) return@withContext false
            writeRange(groupGuid, semesterStart.toString(), semesterEnd.toString(), remote)
            syncClassPeriods()
            cacheScheduleSnapshot(groupGuid, remote)
            true
        }

    /** Локальный офлайн-кэш расписания группы на семестр (JSON в SQLite). */
    private suspend fun cacheScheduleSnapshot(groupGuid: String, items: List<WeekLesson>) =
        withContext(Dispatchers.Default) {
            val json = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(WeekLesson.serializer()), items
            )
            metaQ.setMeta(SNAPSHOT_KEY + groupGuid, json)
        }

    suspend fun cachedSchedule(groupGuid: String): List<WeekLesson>? =
        withContext(Dispatchers.Default) {
            val json = metaQ.getMeta(SNAPSHOT_KEY + groupGuid).executeAsOneOrNull()?.value
                ?: return@withContext null
            runCatching {
                kotlinx.serialization.json.Json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(WeekLesson.serializer()), json
                )
            }.getOrNull()
        }

    suspend fun cachedGroupNames(): List<String> = withContext(Dispatchers.Default) {
        metaQ.getMeta(GROUPS_CACHE_KEY).executeAsOneOrNull()?.value
            ?.split("\n")?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    /** Справочник времени пар: API → SQLite. Сохраняется один раз вместе с расписанием. */
    private suspend fun syncClassPeriods() = withContext(Dispatchers.Default) {
        val periods = runCatching { restClient.getClassPeriods() }.getOrNull() ?: return@withContext
        val now = kotlin.time.Clock.System.now().toString()
        db.transaction {
            for (p in periods) {
                val start = p.startTime ?: continue
                val end = p.endTime ?: continue
                periodsQ.upsertPeriod(p.periodNumber.toLong(), start, end, now)
            }
        }
    }

    private fun writeRange(groupGuid: String, from: String, to: String, items: List<WeekLesson>) {
        val now = kotlin.time.Clock.System.now().toString()
        db.transactionWithResult<Unit>(false) {
            teachersQ.unlinkByLessonIds(groupGuid, from, to)
            lessonsQ.deleteByGroupAndRange(groupGuid, from, to)
            for (l in items) {
                val day = l.day ?: continue
                val discipline = l.discipline ?: continue
                lessonsQ.insertLesson(
                    group_guid = groupGuid,
                    day = day,
                    period = l.period.toLong(),
                    discipline = discipline,
                    workload_type = l.workloadType,
                    room = l.room,
                    building = l.building,
                    campus = l.campus,
                    subgroup = l.subgroup,
                    approved = if (l.approved) 1L else 0L,
                    synced_at = now,
                )
                // last_insert_rowid() — обычный SELECT, SQLDelight генерирует Query<Long>,
                // вызывается напрямую (никакого asQuery здесь нет).
                val lessonId: Long = lessonsQ.lastInsertRowId().executeAsOne()
                val names = LinkedHashSet<String>()
                l.teacherName?.let { names.add(it) }
                names.addAll(l.teacherNames.filterNotNull())
                names.addAll(l.instructorNames.filterNotNull())
                for (name in names) {
                    val slug = slugify(name)
                    teachersQ.upsertTeacher(slug, name)
                    teachersQ.linkLessonTeacher(lessonId, slug)
                }
            }
        }
    }

    /** Есть ли в базе хоть одно занятие группы (т.е. расписание уже синхронизировано). */
    suspend fun hasLessons(groupGuid: String): Boolean = withContext(Dispatchers.Default) {
        lessonsQ.countByGroup(groupGuid).executeAsOne() > 0L
    }

    // ------------------------------------------------------------- Teachers

    suspend fun syncTeachers(): Int = withContext(Dispatchers.Default) {
        val teachers = runCatching { restClient.getTeachers() }.getOrNull() ?: return@withContext 0
        db.transaction {
            for (t in teachers) {
                val slug = t.teacher_slug ?: continue
                val name = t.teacher_name ?: continue
                teachersQ.upsertTeacher(slug, name)
                teachersQ.replaceDepartments(slug)
                for (d in t.departments.filterNotNull()) {
                    teachersQ.insertDepartment(slug, d)
                }
            }
        }
        teachers.size
    }

    /** Кафедры преподавателя по ФИО (для раскрытия деталей занятия). Синхронизирует справочник при пустой БД. */
    suspend fun departmentsForTeacher(name: String): List<String> = withContext(Dispatchers.Default) {
        val slug = slugify(name)
        val cached = teachersQ.departmentsForTeacher(slug).executeAsList()
        if (cached.isNotEmpty()) return@withContext cached
        syncTeachers()
        teachersQ.departmentsForTeacher(slug).executeAsList()
    }

    companion object {
        internal val restClient = RestClient()
        private const val SNAPSHOT_KEY = "snapshot_"
        private const val GROUPS_CACHE_KEY = "groups_cache"

        /** Нормализация ФИО → slug (как в исходном API: "Иван Иванов" → "иван-иванов"). */
        fun slugify(name: String): String =
            name.trim().lowercase().replace(Regex("\\s+"), "-")
    }
}

private fun rea.ohyeahworld.plesson.db.Groups.toEntity(): Group =
    Group(
        group_guid = group_guid,
        group_name = group_name,
        faculty = faculty,
        program_type = program_type,
        course = course,
    )
