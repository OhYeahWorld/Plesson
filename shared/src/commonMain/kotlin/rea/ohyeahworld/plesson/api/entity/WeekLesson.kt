package rea.ohyeahworld.plesson.api.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ответ RPC group_week: расписание группы на неделю (Пн–Сб).
 * Используется слоем синхронизации (SyncManager) для наполнения локальной SQLite.
 */
@Serializable
data class WeekLesson(
    @SerialName("day") val day: String? = null,
    @SerialName("period") val period: Int = 0,
    @SerialName("discipline") val discipline: String? = null,
    @SerialName("workload_type") val workloadType: String? = null,
    @SerialName("room") val room: String? = null,
    @SerialName("building") val building: String? = null,
    @SerialName("campus") val campus: String? = null,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("teacher_names") val teacherNames: List<String> = emptyList(),
    @SerialName("instructor_names") val instructorNames: List<String> = emptyList(),
    @SerialName("subgroup") val subgroup: String? = null,
    @SerialName("approved") val approved: Boolean = true,
)

/** Ответ /rest/v1/class_periods — реальное время пары по её номеру. */
@Serializable
data class ClassPeriod(
    @SerialName("period_number") val periodNumber: Int = 0,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
)

/** Ответ /rest/v1/teachers_current — преподаватель и его кафедры. */
@Serializable
data class Teacher(
    @SerialName("teacher_slug") val teacher_slug: String? = null,
    @SerialName("teacher_name") val teacher_name: String? = null,
    @SerialName("departments") val departments: List<String?> = emptyList(),
)
