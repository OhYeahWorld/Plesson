package rea.ohyeahworld.plesson.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import rea.ohyeahworld.plesson.PlessonDatabase
import rea.ohyeahworld.plesson.api.RestClient
import rea.ohyeahworld.plesson.api.entity.Group
import rea.ohyeahworld.plesson.api.entity.Lesson
import rea.ohyeahworld.plesson.api.entity.WeekLesson
import rea.ohyeahworld.plesson.api.generateDemoWeek
import rea.ohyeahworld.plesson.api.sanitizeLikeParam

/**
 * Data Access Layer (Тема 3.1): единственный источник данных для UI — локальная SQLite.
 * Синхронизация наполняет её из внешнего API pleh.tech; при отсутствии сети
 * используется детерминированный демо-набор, чтобы приложение всегда что-то показывало.
 */
class ScheduleRepository(private val db: PlessonDatabase) {

    private val queries = db.plessonQueries

    /** Понедельник недели, содержащей [date]. */
    fun weekMonday(date: LocalDate): LocalDate =
        date.minus((date.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)

    fun weekSunday(date: LocalDate): LocalDate = weekMonday(date).plus(6L, DateTimeUnit.DAY)

    // ---------------------------------------------------------------- Groups

    suspend fun loadGroups(): List<Group> = withContext(Dispatchers.IO) {
        queries.selectAllGroups().executeAsList().map { it.toEntity() }
    }

    suspend fun searchGroups(query: String): List<Group> = withContext(Dispatchers.IO) {
        val q = sanitizeLikeParam(query)
        if (q.isEmpty()) queries.selectAllGroups().executeAsList().map { it.toEntity() }
        else queries.searchGroups(q).executeAsList().map { it.toEntity() }
    }

    suspend fun upsertGroup(group: Group) = withContext(Dispatchers.IO) {
        val guid = group.group_guid ?: return@withContext
        queries.upsertGroup(
            guid = guid,
            name = group.group_name ?: guid,
            faculty = group.faculty,
            program_type = group.program_type,
            course = group.course,
        )
    }

    /** Синхронизация справочника групп: API → SQLite. Возвращает число сохранённых групп. */
    suspend fun syncGroups(): Int = withContext(Dispatchers.IO) {
        val remote = runCatching { restClient.getGroups() }.getOrNull() ?: return@withContext 0
        db.transaction {
            for (g in remote) {
                val guid = g.group_guid ?: continue
                queries.upsertGroup(
                    guid = guid,
                    name = g.group_name ?: guid,
                    faculty = g.faculty,
                    program_type = g.program_type,
                    course = g.course,
                )
            }
        }
        remote.size
    }

    // -------------------------------------------------------------- Lessons

    /** Расписание на неделю из БД (UI читает только отсюда — работает офлайн). */
    suspend fun lessonsForWeek(groupGuid: String, anyDateOfTheWeek: LocalDate): List<Lesson> =
        withContext(Dispatchers.IO) {
            val from = weekMonday(anyDateOfTheWeek).toString()
            val to = weekSunday(anyDateOfTheWeek).toString()

            // 1) сами занятия
            val rows = queries.selectByGroupAndRange(groupGuid, from, to).executeAsList()

            // 2) все преподаватели каждого занятия (связь M:M через lesson_teachers)
            val teacherNamesByLesson = queries.teachersForLessons(groupGuid, from, to)
                .executeAsList()
                .groupBy({ it.lesson_id }, { it.teacher_name })

            rows.map { row ->
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
                )
            }
        }

    /**
     * Синхронизация недели: API → транзакция → SQLite.
     * Возвращает true, если данные взяты из сети; false — если применён демо-режим.
     */
    suspend fun syncWeek(groupGuid: String, anyDateOfTheWeek: LocalDate): Boolean =
        withContext(Dispatchers.IO) {
            val monday = weekMonday(anyDateOfTheWeek)
            val from = monday.toString()
            val to = weekSunday(anyDateOfTheWeek).toString()

            val remote: List<WeekLesson>? = runCatching {
                restClient.getWeekSchedule(groupGuid, from, to)
            }.getOrNull()?.takeIf { it.isNotEmpty() }

            val source: List<WeekLesson> = remote ?: generateDemoWeek(groupGuid, anyDateOfTheWeek)
            writeWeek(groupGuid, from, to, source)
            remote != null
        }

    private fun writeWeek(groupGuid: String, from: String, to: String, items: List<WeekLesson>) {
        val now = Clock.System.now().toString()
        db.transactionWithResult {
            queries.unlinkByLessonIds(groupGuid, from, to)
            queries.deleteByGroupAndRange(groupGuid, from, to)
            for (l in items) {
                val day = l.day ?: continue
                val discipline = l.discipline ?: continue
                queries.insertLesson(
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
                val lessonId = queries.lastInsertRowId().executeAsOne()
                val names = LinkedHashSet<String>()
                l.teacherName?.let { names.add(it) }
                names.addAll(l.teacherNames.filterNotNull())
                names.addAll(l.instructorNames.filterNotNull())
                for (name in names) {
                    val slug = slugify(name)
                    queries.upsertTeacher(slug, name)
                    queries.linkLessonTeacher(lessonId, slug)
                }
            }
        }
    }

    // ------------------------------------------------------------- Teachers

    suspend fun syncTeachers(): Int = withContext(Dispatchers.IO) {
        val teachers = runCatching { restClient.getTeachers() }.getOrNull() ?: return@withContext 0
        db.transaction {
            for (t in teachers) {
                val slug = t.teacher_slug ?: continue
                val name = t.teacher_name ?: continue
                queries.upsertTeacher(slug, name)
                queries.replaceDepartments(slug)
                for (d in t.departments.filterNotNull()) {
                    queries.insertDepartment(slug, d)
                }
            }
        }
        teachers.size
    }

    /** Кафедры преподавателя по ФИО (для раскрытия деталей занятия). */
    suspend fun departmentsForTeacher(name: String): List<String> = withContext(Dispatchers.IO) {
        queries.departmentsForTeacher(slugify(name)).executeAsList().map { it.department }
    }

    companion object {
        internal val restClient = RestClient()

        /** Нормализация ФИО → slug (как в исходном API: "Иван Иванов" → "иван-иванов"). */
        fun slugify(name: String): String =
            name.trim().lowercase().replace(Regex("\\s+"), "-")
    }
}

private fun rea.ohyeahworld.plesson.Group.toEntity(): Group =
    Group(
        group_guid = group_guid,
        group_name = group_name,
        faculty = faculty,
        program_type = program_type,
        course = course,
    )

