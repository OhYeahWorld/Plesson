package rea.ohyeahworld.plesson.api

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Демо-расписание для запуска без сети (каникулы, нет интернета и т.п.).
 * Генерируется детерминированно по группе и дате: одинаковые данные
 * возвращаются на одном и том же вводе на всех платформах.
 */
private val demoDisciplines = listOf(
    "Математический анализ",
    "Объектно-ориентированное программирование",
    "Базы данных",
    "Дискретная математика",
    "Физика",
    "Иностранный язык",
    "История",
    "Основы алгоритмов и структур данных",
)

private val demoWorkloadTypes = listOf("Лекция", "Практическое занятие", "Лабораторная работа")

private val demoRooms = listOf("415", "208", "121", "305Н", "512", "Ул. Покровка, 11")

private val demoBuildings = listOf("1 корпус", "3 корпус", "Учебный корпус №4")

private val demoTeachers = listOf(
    "Макаренков Иван Сергеевич",
    "Аббясова Диана Рустямовна",
    "Иванов Алексей Петрович",
    "Петрова Мария Сергеевна",
    "Сидоров Николай Викторович",
)

private fun weekMondayOf(date: LocalDate): LocalDate =
    date.minus((date.dayOfWeek.isoDayNumber - 1).toLong(), kotlinx.datetime.DateTimeUnit.DAY)

private fun hash(s: String): Int {
    var h = 0
    for (c in s) h = (h * 31 + c.code) and 0x7FFFFFFF
    return h
}

/**
 * Возвращает занятия на неделю (Пн–Вс), содержащую [date].
 * Выходные (Вс) пустые; у части дней занятий нет — как в реальной жизни.
 */
fun generateDemoWeek(groupGuid: String, date: LocalDate): List<rea.ohyeahworld.plesson.api.entity.WeekLesson> {
    val monday = weekMondayOf(date)
    val seed = hash(groupGuid)
    val lessons = mutableListOf<rea.ohyeahworld.plesson.api.entity.WeekLesson>()

    for (dayOffset in 0..5) { // Пн–Сб
        val d = monday.plus(dayOffset.toLong(), kotlinx.datetime.DateTimeUnit.DAY)
        val daySeed = seed + d.dayOfMonth * 7 + d.monthNumber * 13
        val lessonCount = if (d.dayOfWeek == DayOfWeek.SUNDAY) 0 else (daySeed % 4)
        for (i in 0 until lessonCount) {
            val period = i * 2 + 1 + (i % 2) // пары через одну: 1,3,5,7...
            val discipline = demoDisciplines[(daySeed + i * 3) % demoDisciplines.size]
            val teacher = demoTeachers[(daySeed + i) % demoTeachers.size]
            lessons += rea.ohyeahworld.plesson.api.entity.WeekLesson(
                day = d.toString(),
                period = period,
                discipline = discipline,
                workloadType = demoWorkloadTypes[(daySeed + i * 5) % demoWorkloadTypes.size],
                room = demoRooms[(daySeed + i * 2) % demoRooms.size],
                building = demoBuildings[(daySeed + i) % demoBuildings.size],
                campus = "Основная",
                teacherName = teacher,
                teacherNames = listOf(teacher),
                instructorNames = listOf(teacher),
                subgroup = null,
                approved = true,
            )
        }
    }
    return lessons
}
