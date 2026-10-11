package rea.ohyeahworld.plesson

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import rea.ohyeahworld.plesson.api.entity.Lesson

/** UI-модель дня для оптимизации перерисовок. */
data class DayUi(
    val date: String,
    val dayOfWeekText: String,
    val isToday: Boolean,
    val lessons: List<Lesson>,
)

/** Понедельник недели, содержащей [date]. */
fun mondayOf(date: LocalDate): LocalDate =
    date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))

/** "HH:mm:ss" -> "HH:mm". */
fun shortTime(t: String?): String? = t?.split(":")?.takeIf { it.size >= 2 }?.joinToString(":") { p -> p.take(2) }

/**
 * Дни выбранной недели: Пн–Сб всегда (пустые тоже показываем), Вс — только если там есть занятия.
 * Внутри дня занятия упорядочены по реальному времени начала; при его отсутствии — по номеру пары.
 */
fun buildDaysUi(
    weekStart: LocalDate,
    lessons: List<Lesson>,
    todayStr: String,
): List<DayUi> {
    val names = mapOf(1 to "Пн", 2 to "Вт", 3 to "Ср", 4 to "Чт", 5 to "Пт", 6 to "Сб", 7 to "Вс")
    val byDate = lessons.groupBy { it.day }
        .mapValues { (_, ls) ->
            ls.sortedWith(compareBy({ it.startTime ?: "~" }, { it.period?.toIntOrNull() ?: Int.MAX_VALUE }))
        }
    val result = mutableListOf<DayUi>()
    var d = weekStart
    repeat(6) { // Пн–Сб
        val iso = d.toString()
        result += DayUi(
            date = iso,
            dayOfWeekText = names[d.dayOfWeek.isoDayNumber] ?: "",
            isToday = iso == todayStr,
            lessons = byDate[iso] ?: emptyList(),
        )
        d = d.plus(DatePeriod(days = 1))
    }
    // Воскресенье показываем только если там есть занятия
    val sundayLessons = byDate[d.toString()] ?: emptyList()
    if (sundayLessons.isNotEmpty()) {
        result += DayUi(d.toString(), "Вс", d.toString() == todayStr, sundayLessons)
    }
    return result
}
