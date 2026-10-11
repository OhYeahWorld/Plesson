package rea.ohyeahworld.plesson.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import rea.ohyeahworld.plesson.api.entity.ClassPeriod
import rea.ohyeahworld.plesson.api.entity.Group
import rea.ohyeahworld.plesson.api.entity.Teacher
import rea.ohyeahworld.plesson.api.entity.WeekLesson

/**
 * Сетевой слой (Data Source): API-клиент для PostgREST pleh.tech.
 * Единственная задача — получить "сырые" данные; сохранением в SQLite занимается ScheduleRepository.
 */
expect fun createHttpClient(): HttpClient

private const val PAGE_SIZE = 1000

class RestClient {
    private val groupsUrl = "https://pleh.tech/rest/v1/groups_current"
    private val teachersUrl = "https://pleh.tech/rest/v1/teachers_current"
    private val weekRpcUrl = "https://pleh.tech/rest/v1/rpc/group_week"
    private val classPeriodsUrl = "https://pleh.tech/rest/v1/class_periods"

    private val client = createHttpClient()

    /**
     * Список всех групп (справочник). PostgREST отдаёт не более 1000 строк за запрос,
     * всего групп больше — поэтому читаем страницами по offset до последнего неполного ответа.
     */
    suspend fun getGroups(): List<Group> {
        val result = mutableListOf<Group>()
        var offset = 0
        while (true) {
            val page: List<Group> = client.get(groupsUrl) {
                parameter("select", "group_guid,group_name,faculty,program_type,course")
                parameter("order", "group_name.asc")
                parameter("limit", PAGE_SIZE)
                parameter("offset", offset)
            }.body()
            result += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return result
    }

    /** Поиск групп по подстроке названия (ilike на стороне сервера). */
    suspend fun getGroups(name: String): List<Group> = client.get(groupsUrl) {
        parameter("select", "group_guid,group_name,faculty,program_type,course")
        parameter("order", "group_name.asc")
        parameter("limit", PAGE_SIZE)
        parameter("group_name", "ilike.*${sanitizeLikeParam(name)}*")
    }.body()

    /** Преподаватели и их кафедры. */
    suspend fun getTeachers(): List<Teacher> = client.get(teachersUrl) {
        parameter("select", "teacher_slug,teacher_name,departments")
        parameter("order", "teacher_name.asc")
    }.body()

    /** Справочник реального времени пар (период N -> начало/конец). */
    suspend fun getClassPeriods(): List<ClassPeriod> = client.get(classPeriodsUrl) {
        parameter("order", "period_number.asc")
    }.body()

    /**
     * Одна неделя расписания группы через RPC group_week.
     * Сервер допускает интервал не длиннее недели ("invalid range" при большем),
     * поэтому семестр загружается побуквенно неделями (см. ScheduleRepository.syncSemester).
     */
    suspend fun getScheduleWeek(groupGuid: String, from: String, to: String): List<WeekLesson> =
        client.get(weekRpcUrl) {
            parameter("p_group_guid", groupGuid)
            parameter("p_from", from)
            parameter("p_to", to)
            parameter("order", "day.asc,period.asc")
            parameter(
                "select",
                "day,period,discipline,workload_type,room,building,campus," +
                        "teacher_name,teacher_names,instructor_names,instructor_departments,subgroup,approved"
            )
        }.body()
}
