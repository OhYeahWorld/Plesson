package rea.ohyeahworld.plesson.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import platform.Foundation.NSURLSessionConfiguration

actual fun createHttpClient(): HttpClient {
    return HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        engine {
            // Создаем конфигурацию сессии
            val sessionConfig = NSURLSessionConfiguration.defaultSessionConfiguration
            sessionConfig.setAllowsCellularAccess(true)

            // Применяем конфигурацию
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }
}