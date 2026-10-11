package rea.ohyeahworld.plesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import rea.ohyeahworld.plesson.api.entity.Group
import rea.ohyeahworld.plesson.api.entity.Lesson
import rea.ohyeahworld.plesson.color.MyScheduleTheme
import rea.ohyeahworld.plesson.db.ScheduleRepository
import rea.ohyeahworld.plesson.service.EmptyDayView
import rea.ohyeahworld.plesson.service.LessonCreateView
import kotlin.time.Clock

/** UI-модель дня для оптимизации перерисовок. */
data class DayUi(
    val date: String,
    val dayOfWeekText: String,
    val isToday: Boolean,
    val lessons: List<Lesson>,
)

/**
 * Единственный источник данных приложения — локальная база SQLite (SQLDelight).
 * На Android задаётся из MainActivity (нужен Context), на остальных платформах
 * создаётся лениво через платформенную фабрику драйверов (expect/actual).
 */
object AppDatabase {
    var repository: ScheduleRepository? = null

    fun ensure(): ScheduleRepository? {
        repository?.let { return it }
        val driver = runCatching { createSqlDriver() }.getOrNull() ?: return null
        val db = PlessonDatabase(driver)
        return ScheduleRepository(db).also { repository = it }
    }
}

private const val DEFAULT_GROUP_GUID = "38d57430-cbfd-11ee-bec3-005056912e01"
private const val DEFAULT_GROUP_NAME = "15.27Д-ИВТ01/23б"

@Composable
@Preview
fun App() {
    val repo = remember { AppDatabase.ensure() }

    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var groupGuid by remember { mutableStateOf(DEFAULT_GROUP_GUID) }
    var currentGroup by remember { mutableStateOf(DEFAULT_GROUP_NAME) }

    // Текущая просматриваемая неделя — её понедельник
    val todayDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var weekStart by remember { mutableStateOf(mondayOf(todayDate)) }

    var daysUi by remember { mutableStateOf<List<DayUi>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var offlineMode by remember { mutableStateOf(false) }

    val todayStr = remember(todayDate) { todayDate.toString() }

    /** Загрузка недели: сначала читаем SQLite, затем синхронизируем с API и перечитываем. */
    suspend fun loadWeek(date: LocalDate, syncFirst: Boolean) {
        if (repo == null) {
            errorText = "База данных недоступна на этой платформе"
            isLoading = false
            return
        }
        errorText = null
        try {
            isLoading = true
            if (syncFirst) {
                val online = repo.syncWeek(groupGuid, date)
                offlineMode = !online
            }
            val lessons = repo.lessonsForWeek(groupGuid, date)
            daysUi = buildDaysUi(date, lessons, todayStr)
        } catch (e: Exception) {
            // Тема 3.3: единый обработчик ошибок — показываем осмысленное сообщение
            errorText = humanError(e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(groupGuid, weekStart) {
        loadWeek(weekStart, syncFirst = true)
        if (repo != null && groups.isEmpty()) {
            runCatching { repo.loadGroups() }.getOrNull()?.let { groups = it }
        }
    }

    val pagerState = rememberPagerState(pageCount = { daysUi.size })
    val lazyListState = rememberLazyListState()

    // При загрузке недели — сразу на сегодняшний день (или первый учебный вперёд)
    LaunchedEffect(daysUi) {
        if (daysUi.isNotEmpty()) {
            val targetPage = daysUi.indexOfFirst { it.isToday }.takeIf { it >= 0 }
                ?: daysUi.indexOfFirst { LocalDate.parse(it.date) >= todayDate && it.lessons.isNotEmpty() }
                    .takeIf { it >= 0 }
                ?: 0
            pagerState.scrollToPage(targetPage.coerceAtMost(daysUi.lastIndex))
        }
    }

    // Синхронизация чипов дат и страницы Pager
    LaunchedEffect(pagerState.currentPage) {
        if (daysUi.isNotEmpty()) {
            lazyListState.animateScrollToItem(pagerState.currentPage.coerceAtMost(daysUi.lastIndex))
        }
    }

    MyScheduleTheme {
        var showGroupPicker by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---------- Шапка: группа + переключение недель (ключевые действия всегда видны)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { showGroupPicker = !showGroupPicker }) {
                    Text(text = currentGroup, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                // ← назад на неделю
                OutlinedButton(onClick = { weekStart = weekStart.minus(7L, DateTimeUnit.DAY) }) {
                    Text("←")
                }
                // → вперёд на неделю (после субботы открывается следующая неделя)
                OutlinedButton(onClick = { weekStart = weekStart.plus(7L, DateTimeUnit.DAY) }) {
                    Text("→")
                }
            }

            if (offlineMode && !isLoading) {
                Text(
                    text = "Нет соединения — показаны демо-данные",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                errorText != null -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorText.orEmpty(), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { coroutineScope.launch { loadWeek(weekStart, true) } }) {
                                Text("Повторить")
                            }
                        }
                    }
                }

                daysUi.isNotEmpty() -> {
                    LazyRow(
                        state = lazyListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(daysUi) { index, day ->
                            DayChip(
                                day = day,
                                isSelected = pagerState.currentPage == index,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            )
                        }
                    }

                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        val currentDay = daysUi[page]
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = prettyDate(currentDay.date),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (currentDay.lessons.isEmpty()) {
                                    item { EmptyDayView() }
                                } else {
                                    items(
                                        currentDay.lessons,
                                        key = { "${it.id}-${it.period}-${it.subgroup}" },
                                    ) { lesson ->
                                        LessonCreateView(lesson) { name ->
                                            repo?.departmentsForTeacher(name) ?: emptyList()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- Выбор группы (Master-Detail: список групп из SQLite)
            AnimatedVisibility(showGroupPicker) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var fieldText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = fieldText,
                        onValueChange = { fieldText = it },
                        singleLine = true,
                        label = { Text("Найти группу") },
                        placeholder = { Text("Например: 15.27Д-ИВТ01/23б") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    )
                    val filteredList = remember(fieldText, groups) {
                        if (fieldText.isBlank()) groups
                        else groups.filter { it.group_name?.contains(fieldText, ignoreCase = true) == true }
                    }
                    Text(
                        text = "Групп найдено: ${filteredList.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                    ) {
                        items(filteredList.take(100)) { group ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        group.group_guid?.let { guid ->
                                            groupGuid = guid
                                            currentGroup = group.group_name.orEmpty()
                                        }
                                        showGroupPicker = false
                                        keyboardController?.hide()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = group.group_name.orEmpty(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val subtitle = listOfNotNull(group.faculty, group.program_type, group.course)
                                    .joinToString(" · ")
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun mondayOf(date: LocalDate): LocalDate =
    date.minus((date.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)

private fun prettyDate(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else iso
}

/** Тема 3.3: перевод системных ошибок в человеческий текст. */
private fun humanError(e: Exception): String {
    val msg = e.message.orEmpty().lowercase()
    return when {
        "host" in msg || "network" in msg || "connect" in msg || "timeout" in msg ||
            e::class.simpleName.orEmpty().contains("IOException") ->
            "Нет подключения к серверу расписания. Проверьте интернет."
        else -> "Ошибка: ${e.message ?: e::class.simpleName}"
    }
}

/** Собирает список дней Пн–Сб выбранной недели (пустые дни тоже показываем). */
private fun buildDaysUi(
    weekStart: LocalDate,
    lessons: List<Lesson>,
    todayStr: String,
): List<DayUi> {
    val names = mapOf(1 to "Пн", 2 to "Вт", 3 to "Ср", 4 to "Чт", 5 to "Пт", 6 to "Сб", 7 to "Вс")
    val byDate = lessons.groupBy { it.day }
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
        d = d.plus(1L, DateTimeUnit.DAY)
    }
    // Воскресенье показываем только если там есть занятия
    val sundayLessons = byDate[d.toString()] ?: emptyList()
    if (sundayLessons.isNotEmpty()) {
        result += DayUi(d.toString(), "Вс", d.toString() == todayStr, sundayLessons)
    }
    return result
}

// Легковесный чип дня
@Composable
fun DayChip(
    day: DayUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (day.isToday) "${day.dayOfWeekText} •" else day.dayOfWeekText,
                color = contentColor,
                fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
            val dateParts = day.date.split("-")
            if (dateParts.size == 3) {
                Text(
                    text = "${dateParts[2]}.${dateParts[1]}",
                    color = contentColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
