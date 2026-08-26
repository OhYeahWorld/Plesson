package rea.ohyeahworld.plesson.color

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 1. Создаем цвета прямо здесь, чтобы проект не ругался на ошибки


// Светлая схема
private val MyCustomColorScheme = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextGray,
    secondary = LightBlue,
    background = lightBackground,       // Твой тестовый желтый фон экрана
    surface = Color.White           // Белый фон для подложек
)

// Тёмная схема
private val MyDarkColorScheme = darkColorScheme(
    primary = LightBlue,
    background = darkBackground,
    surface = Color(0xFF121212)
)

@Composable
fun MyScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit // Убрал лишнюю запятую в конце
) {
    // Автоматически выбираем нужную схему
    val colorScheme = if (darkTheme) MyDarkColorScheme else MyCustomColorScheme

    MaterialTheme(
        colorScheme = colorScheme, // Передаем правильную схему (не MyCustomColorScheme!)
        content = content
    )
}
