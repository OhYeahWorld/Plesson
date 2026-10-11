package rea.ohyeahworld.plesson.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Общие настройки Ktor-клиента для всех платформ (заголовки, таймауты, JSON). */
fun HttpClientConfig<*>.applyCommonConfig(apiKey: String) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }

    defaultRequest {
        headers {
            append("apikey", apiKey)
            append("Authorization", "Bearer $apiKey")
            append("Accept-Profile", "api")
            append(HttpHeaders.Accept, "application/json")
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:157.0) Gecko/20100101 Firefox/157.0"
            )
        }
    }

    expectSuccess = false
}
