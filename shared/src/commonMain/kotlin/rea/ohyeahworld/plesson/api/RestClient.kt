package rea.ohyeahworld.plesson.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import rea.ohyeahworld.plesson.api.entity.Day
import rea.ohyeahworld.plesson.api.entity.Group
import rea.ohyeahworld.plesson.api.entity.Lesson
import kotlin.time.Clock

expect fun createHttpClient(): HttpClient

class RestClient {
    val GROUPS_URL = "https://pleh.tech/rest/v1/groups_current";
    val SCHEDULE_URL = "https://pleh.tech/rest/v1/lessons_public"
    val TOKEN =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiwiaWF0IjoxNzgxNDM2NjY5LCJleHAiOjIwOTY3OTY2Njl9.uVBH4rCUVsarTmNqhiKBw8kvLqKygVvs31fkWW0C6u0"

    val client = createHttpClient()

    suspend fun getGroups(): List<Group> {
        return client.get(GROUPS_URL) {
            bearerAuth(TOKEN)                       // Authorization: Bearer <token>
            header("apiKey", TOKEN)                 // apiKey: <token>
            header("Accept-Profile", "api")         // схема PostgREST
            parameter("order", "group_name.asc")
            parameter("select", "group_guid,group_name,faculty,program_type,course")
        }.body()
    }

    suspend fun getGroups(name: String): List<Group> {
        return client.get(GROUPS_URL) {
            bearerAuth(TOKEN)                       // Authorization: Bearer <token>
            header("apiKey", TOKEN)                 // apiKey: <token>
            header("Accept-Profile", "api")         // схема PostgREST
            parameter("order", "group_name.asc")
            parameter("select", "group_guid,group_name,faculty,program_type,course")
            parameter("group_name", "ilike.*$name*")
        }.body()
    }

    suspend fun getLessonsByGroupGuid(group_guid: String) : List<Lesson> {
        var today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        // Вычисляем начало семестра (1 сентября текущего или прошлого года)
        val year = if (today.month.number in 8..12) today.year else today.year - 1
        val gte = "gte.${year}-09-01"

        val lte = "lte." + if (today.month.number in 6..12) "${today.year}-12-31" else "${today.year}-08-01"

        val response = client.get(SCHEDULE_URL) {
            bearerAuth(TOKEN)
            header("apiKey", TOKEN)
            header("Accept-Profile", "api")
            parameter("group_guid", "eq.$group_guid")

            // Используем корректный gte от начала семестра
            url.parameters.append("day", gte)
            url.parameters.append("day", lte)

            parameter("order", "day.asc,period.asc")
            parameter(
                "select", "day,period," +
                        "discipline," +
                        "workload_type," +
                        "room," +
                        "building," +
                        "campus," +
                        "teacher_name," +
                        "instructor_departments"
            )
        }
        return response.body()
    }

    private fun getWeekInterval(): Pair<String, String> {
        var today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        if (today.month.number in 6..8) {
            today = LocalDate(today.year, 9, 1)
        }
        val dayOfWeekNumber = today.dayOfWeek.isoDayNumber


        // Вычитаем и добавляем дни через DateTimeUnit
        val startOfWeek = today.minus(dayOfWeekNumber - 1, DateTimeUnit.DAY)
        val endOfWeek = today.plus(7 - dayOfWeekNumber, DateTimeUnit.DAY)

        return Pair(startOfWeek.toString(), endOfWeek.toString())
    }

    suspend fun getWeekSchedule(guid: String): List<Day> {
        val lessons = getLessonsByGroupGuid(guid)
        val schedule: List<Day> = lessons.groupBy { it.day }.map { (date, lessonsInDay) ->
            Day(
                date,
                lessonsInDay
            )
        }
        return schedule
    }

}