package rea.ohyeahworld.plesson.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import rea.ohyeahworld.plesson.api.entity.Day
import rea.ohyeahworld.plesson.api.entity.Lesson
import kotlin.time.Clock

var today = Clock.System.todayIn(TimeZone.currentSystemDefault())

// Функция для определения начальной страницы
fun getInitialPage(days: List<Day>, currentDayOfWeek: Int): Int {
    if (days.isEmpty()) return 0

    // Ищем индекс сегодняшнего дня в расписании
    val todayIndex = days.indexOfFirst { day ->
        day.date?.let { dateString ->
            // Парсим дату из строки и сравниваем день недели
            try {
                val dateParts = dateString.split("-")
                if (dateParts.size == 3) {
                    val year = dateParts[0].toInt()
                    val month = dateParts[1].toInt()
                    val dayOfMonth = dateParts[2].toInt()
                    val localDate = kotlinx.datetime.LocalDate(year, month, dayOfMonth)
                    localDate.dayOfWeek.isoDayNumber == currentDayOfWeek
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    // Если сегодня есть в расписании - показываем его
    if (todayIndex != -1) {
        return todayIndex
    }

    // Если сегодня нет в расписании (выходной/каникулы), ищем следующий рабочий день
    for (i in 1..7) { // Проверяем следующие 7 дней
        val nextDayOfWeek = ((currentDayOfWeek + i - 1) % 7) + 1
        val nextDayIndex = days.indexOfFirst { day ->
            day.date?.let { dateString ->
                try {
                    val dateParts = dateString.split("-")
                    if (dateParts.size == 3) {
                        val year = dateParts[0].toInt()
                        val month = dateParts[1].toInt()
                        val dayOfMonth = dateParts[2].toInt()
                        val localDate = kotlinx.datetime.LocalDate(year, month, dayOfMonth)
                        localDate.dayOfWeek.isoDayNumber == nextDayOfWeek
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            } ?: false
        }

        if (nextDayIndex != -1) {
            return nextDayIndex
        }
    }

    // Если ничего не нашли, возвращаем первый день
    return 0
}


@Composable
fun LessonCreateView(lesson: Lesson) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column (
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                lesson.discipline?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Аудитория: ${lesson.room}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Номер пары в красивом кружочке
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = lesson.period.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDayView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Выходной",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = "Занятий нет, можно отдохнуть",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}