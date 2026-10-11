package rea.ohyeahworld.plesson.api.entity

/**
 * Доменная модель занятия — то, что отдаёт Data Access Layer (SQLite) интерфейсу.
 * id — первичный ключ таблицы lessons (для связки с преподавателями M:M).
 */
data class Lesson(
    val id: Long = 0L,
    val day: String?,
    val period: String?,
    val discipline: String?,
    val workload_type: String? = null,
    val room: String? = null,
    val building: String? = null,
    val campus: String? = null,
    val teacher_name: String? = null,
    /** Все преподаватели занятия (связь M:M через lesson_teachers). */
    val teacher_names: List<String> = emptyList(),
    val subgroup: String? = null,
    val approved: Boolean = true,
)

/** День недели с занятиями (агрегация для UI). */
data class Day(
    val date: String?,
    val lessons: List<Lesson>,
)
