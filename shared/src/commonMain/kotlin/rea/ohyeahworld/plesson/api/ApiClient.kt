package rea.ohyeahworld.plesson.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Клиент собственного REST API (Тема 3.2).
 * Приложение больше НЕ ходит напрямую в pleh.tech — все данные идут
 * через серверную часть и PostgreSQL (клиент-серверная архитектура).
 */
class ApiClient {

    @Serializable
    data class GroupItem(
        @SerialName("group_guid") val guid: String,
        val name: String,
        val faculty: String? = null,
        @SerialName("program_type") val programType: String? = null,
        val course: String? = null,
    )

    @Serializable
    data class GroupsResponse(val items: List<GroupItem>, val total: Int = 0)

    @Serializable
    data class LessonDto(
        val day: String,
        val period: Int,
        val discipline: String,
        @SerialName("workload_type") val workloadType: String? = null,
        val room: String? = null,
        val building: String? = null,
        val campus: String? = null,
        @SerialName("teacher_names") val teacherNames: List<String> = emptyList(),
        val subgroup: String? = null,
        val approved: Boolean = true,
        val time: String? = null,
    )

    @Serializable
    data class DayDto(val date: String, val lessons: List<LessonDto>)

    @Serializable
    data class WeekResponse(
        @SerialName("group_guid") val guid: String,
        @SerialName("week_from") val weekFrom: String,
        @SerialName("week_to") val weekTo: String,
        val today: String,
        val days: List<DayDto>,
    )

    @Serializable
    data class TeacherInfo(
        val slug: String,
        @SerialName("full_name") val fullName: String,
        val departments: List<String?> = emptyList(),
    )

    @Serializable
    data class TeachersResponse(val items: List<TeacherInfo> = emptyList())

    @Serializable
    data class TeacherLesson(
        val day: String,
        val period: Int,
        val discipline: String,
        @SerialName("workload_type") val workloadType: String? = null,
        @SerialName("group_name") val groupName: String? = null,
        val room: String? = null,
        val building: String? = null,
        val campus: String? = null,
    )

    @Serializable
    data class TeacherLessonsResponse(val items: List<TeacherLesson> = emptyList())

    private val client = createHttpClient()

    suspend fun groups(q: String = "", limit: Int = 50): GroupsResponse =
        client.get("$BASE/api/groups") {
            parameter("q", q); parameter("limit", limit)
        }.body()

    /** Расписание на неделю, содержащую [date] (Пн–Сб). */
    suspend fun week(guid: String, date: String): WeekResponse =
        client.get("$BASE/api/schedule/$guid") {
            parameter("date", date); parameter("force", true)
        }.body()

    suspend fun teacher(name: String): TeachersResponse =
        client.get("$BASE/api/teachers") { parameter("name", name) }.body()

    suspend fun teacherLessons(name: String, date: String): TeacherLessonsResponse =
        client.get("$BASE/api/teacher-lessons") {
            parameter("name", name); parameter("date", date)
        }.body()

    companion object {
        // Адрес серверной части. Для эмулятора Android: 10.0.2.2, для десктопа/web: localhost.
        const val BASE = "http://localhost:8000"
    }
}
