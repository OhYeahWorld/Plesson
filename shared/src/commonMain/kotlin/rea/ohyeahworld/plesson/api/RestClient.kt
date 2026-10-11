package rea.ohyeahworld.plesson.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import rea.ohyeahworld.plesson.api.entity.Group
import rea.ohyeahworld.plesson.api.entity.Teacher
import rea.ohyeahworld.plesson.api.entity.WeekLesson

/**
 * Сетевой слой (Data Source). Тема 3.1: API-клиент для внешнего PostgREST API pleh.tech.
 * Единственная задача — получить "сырые" данные; сохранением в SQLite занимается SyncManager.
 */
expect fun createHttpClient(): HttpClient

class RestClient {
    private val GROUPS_URL = "https://pleh.tech/rest/v1/groups_current"
    private val TEACHERS_URL = "https://pleh.tech/rest/v1/teachers_current"
    private val WEEK_RPC_URL = "https://pleh.tech/rest/v1/rpc/group_week"
    private val TOKEN =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiwiaWF0IjoxNzgxNDM2NjY5LCJleHAiOjIwOTY3OTY2Njl9.uVBH4rCUVsarTmNqhiKBw8kvLqKygVvs31fkWW0C6u0"

    private val client = createHttpClient()

    /** Список всех групп (справочник). */
    suspend fun getGroups(): List<Group> = client.get(GROUPS_URL) {
        authAndProfile()
        parameter("select", "group_guid,group_name,faculty,program_type,course")
        parameter("order", "group_name.asc")
    }.body()

    /** Поиск групп по подстроке названия (на стороне сервера). */
    suspend fun getGroups(name: String): List<Group> = client.get(GROUPS_URL) {
        authAndProfile()
        parameter("select", "group_guid,group_name,faculty,program_type,course")
        parameter("order", "group_name.asc")
        parameter("group_name", "ilike.*${name.filterNot { it == '%' }}*")
    }.body()

    /** Преподаватели и их кафедры. */
    suspend fun getTeachers(): List<Teacher> = client.get(TEACHERS_URL) {
        authAndProfile()
        parameter("select", "teacher_slug,teacher_name,departments")
        parameter("order", "teacher_name.asc")
    }.body()

    /**
     * Расписание группы на неделю через RPC group_week (Пн–Сб заданной недели).
     * @param monday ISO-дата понедельника нужной недели
     * @param sunday ISO-дата воскресенья этой же недели
     */
    suspend fun getWeekSchedule(groupGuid: String, monday: String, sunday: String): List<WeekLesson> =
        client.get(WEEK_RPC_URL) {
            authAndProfile()
            parameter("p_group_guid", groupGuid)
            parameter("p_from", monday)
            parameter("p_to", sunday)
            parameter("order", "day.asc,period.asc")
            parameter(
                "select",
                "day,period,discipline,workload_type,room,building,campus," +
                        "teacher_name,teacher_names,instructor_names,subgroup,approved"
            )
        }.body()

    private fun HttpRequestBuilder.authAndProfile() {
        bearerAuth(TOKEN)
        header("apiKey", TOKEN)
        header("Accept-Profile", "api")
    }
}
