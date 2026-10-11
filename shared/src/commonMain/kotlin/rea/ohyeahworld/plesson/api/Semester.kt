package rea.ohyeahworld.plesson.api

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Границы текущего семестра.
 * Осенний: 1 сентября – 31 января, весенний: 1 февраля – 30 июня.
 */
object Semester {
    fun bounds(today: LocalDate): Pair<LocalDate, LocalDate> = when (today.month) {
        in Month.SEPTEMBER..Month.DECEMBER ->
            LocalDate(today.year, 9, 1) to LocalDate(today.year + 1, 1, 31)
        Month.JANUARY ->
            LocalDate(today.year - 1, 9, 1) to LocalDate(today.year, 1, 31)
        else -> // Февраль – Август: весенний семестр того же года
            LocalDate(today.year, 2, 1) to LocalDate(today.year, 6, 30)
    }
}
