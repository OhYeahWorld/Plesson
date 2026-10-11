package rea.ohyeahworld.plesson.api

/**
 * Тема 3.1: валидация входных данных на уровне СУБД.
 * Запрещаем подстановку спецсимволов LIKE ('%', '_', "'") в параметр поиска,
 * чтобы запросы к БД оставались безопасными (защита от инъекций).
 */
fun sanitizeLikeParam(raw: String): String =
    raw.filter { it != '%' && it != '_' && it != '\'' }.trim()
