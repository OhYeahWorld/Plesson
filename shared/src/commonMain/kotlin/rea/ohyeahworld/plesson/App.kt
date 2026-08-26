package rea.ohyeahworld.plesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import rea.ohyeahworld.plesson.api.entity.Day
import rea.ohyeahworld.plesson.api.entity.Group
import rea.ohyeahworld.plesson.api.RestClient
import rea.ohyeahworld.plesson.api.entity.Lesson
import rea.ohyeahworld.plesson.color.MyScheduleTheme
import rea.ohyeahworld.plesson.service.EmptyDayView
import rea.ohyeahworld.plesson.service.LessonCreateView

// UI-модель для оптимизации
data class DayUi(
    val date: String,
    val dayOfWeekText: String,
    val isToday: Boolean,
    val lessons: List<Lesson>
)

@Composable
@Preview
fun App() {
    val restClient = remember { RestClient() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var group_guid by remember { mutableStateOf("38d57430-cbfd-11ee-bec3-005056912e01") }

    var daysUi by remember { mutableStateOf<List<DayUi>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val todayDate = remember {
        kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val todayStr = remember { todayDate.toString() }

    LaunchedEffect(group_guid) {
        println("=== НАЧАЛО СЕТЕВОГО ЗАПРОСА ===")
        try {
            isLoading = true
            val schedule = restClient.getWeekSchedule(group_guid)

            if (groups.isEmpty()) {
                val groupsFromRest = restClient.getGroups()
                groups = groupsFromRest
            }

            val dates = schedule.mapNotNull { it.date }

            if (dates.isNotEmpty()) {
                val sortedDates = dates.sorted()
                val minDate = LocalDate.parse(sortedDates.first())
                val maxDate = LocalDate.parse(sortedDates.last())

                val scheduleMap = schedule.associateBy { it.date }
                val fullScheduleUi = mutableListOf<DayUi>()
                var currentDate = minDate

                val fullName = mapOf(
                    1 to "Пн", 2 to "Вт", 3 to "Ср",
                    4 to "Чт", 5 to "Пт", 6 to "Сб", 7 to "Вс"
                )

                while (currentDate <= maxDate) {
                    val dateString = currentDate.toString()
                    val existingDay = scheduleMap[dateString]
                    val lessons = existingDay?.lessons ?: emptyList()

                    val dayNum = currentDate.dayOfWeek.isoDayNumber
                    val dayOfWeekText = fullName[dayNum] ?: ""
                    val isToday = dateString == todayStr

                    fullScheduleUi.add(
                        DayUi(
                            date = dateString,
                            dayOfWeekText = dayOfWeekText,
                            isToday = isToday,
                            lessons = lessons
                        )
                    )
                    currentDate = currentDate.plus(1, DateTimeUnit.DAY)
                }
                daysUi = fullScheduleUi
            } else {
                daysUi = emptyList()
            }
        } catch (e: Exception) {
            println("=== ОШИБКА ЗАПРОСА ===")
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val pagerState = rememberPagerState(pageCount = { daysUi.size })

    // Состояние для LazyRow (ленивый список дат)
    val lazyListState = rememberLazyListState()

    // Автоматически скроллим к нужному дню при первой загрузке
    LaunchedEffect(daysUi) {
        if (daysUi.isNotEmpty()) {
            val targetPage = if (daysUi.any { it.isToday && todayDate.dayOfWeek.isoDayNumber in 1..5 }) {
                daysUi.indexOfFirst { it.isToday }
            } else {
                val nextWorkingDayIdx = daysUi.indexOfFirst { day ->
                    val d = try { LocalDate.parse(day.date) } catch (e: Exception) { null }
                    d != null && d >= todayDate && d.dayOfWeek.isoDayNumber in 1..5
                }
                if (nextWorkingDayIdx != -1) nextWorkingDayIdx
                else daysUi.indexOfLast { day ->
                    val d = try { LocalDate.parse(day.date) } catch (e: Exception) { null }
                    d != null && d < todayDate && d.dayOfWeek.isoDayNumber in 1..5
                }.takeIf { it != -1 } ?: 0
            }

            if (pagerState.currentPage != targetPage && targetPage < daysUi.size) {
                pagerState.scrollToPage(targetPage) // Мгновенный переход без анимации при загрузке
            }
        }
    }

    // Синхронизация: когда свайпаем Pager, скроллим LazyRow к текущему дню
    LaunchedEffect(pagerState.currentPage) {
        if (daysUi.isNotEmpty()) {
            lazyListState.animateScrollToItem(pagerState.currentPage)
        }
    }

    MyScheduleTheme {
        var showContent by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            if (isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (daysUi.isNotEmpty()) {

                // ЗАМЕНА: Используем LazyRow вместо PrimaryScrollableTabRow
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(daysUi) { index, day ->
                        DayChip(
                            day = day,
                            isSelected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val currentDay = daysUi[page]

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Заголовок дня выносим из LazyColumn, чтобы он не прокручивался
                        Text(
                            text = currentDay.date,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.ExtraBold
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp), // Отступы для всего списка
                            verticalArrangement = Arrangement.spacedBy(8.dp), // Расстояние между карточками
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (currentDay.lessons.isEmpty()) {
                                item {
                                    EmptyDayView()
                                }
                            } else {
                                // ПРАВИЛЬНО: используем items вместо захардкоженного forEach
                                items(currentDay.lessons) { lesson ->
                                    LessonCreateView(lesson)
                                }
                            }
                        }
                    }
                }
            }

            var currentGroup by remember { mutableStateOf("Выбрать группу...") }
            var buttonText by remember { mutableStateOf(currentGroup) }

            Button(onClick = {
                showContent = !showContent
                buttonText = if (showContent) "Готово" else currentGroup
            }) {
                Text(buttonText)
            }

            AnimatedVisibility(showContent) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var fieldText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = fieldText,
                        onValueChange = { fieldText = it },
                        singleLine = true,
                        label = { Text("Найти группу") },
                        placeholder = { Text("Например: 15.27Д-ИВТ01/24б") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboardController?.hide() }
                        )
                    )
                    val filteredList = groups.filter {
                        it.group_name?.contains(fieldText, ignoreCase = true) == true
                    }
                    Text(
                        text = "Групп найдено ${filteredList.size}:",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )


                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(filteredList) { group ->
                            Text(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = group.group_name.orEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        group.group_guid?.let { guid ->
                                            group_guid = guid
                                        }
                                        showContent = false
                                        currentGroup = group.group_name.orEmpty()
                                        buttonText = currentGroup
                                    }
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Легковесный кастомный чип для замены тяжелого Tab
@Composable
fun DayChip(
    day: DayUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (day.isToday) "${day.dayOfWeekText} •" else day.dayOfWeekText,
                color = contentColor,
                fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium
            )
            // Показываем дату (например, "26.08" из "2026-08-26")
            val dateParts = day.date.split("-")
            if (dateParts.size == 3) {
                Text(
                    text = "${dateParts[2]}.${dateParts[1]}",
                    color = contentColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
