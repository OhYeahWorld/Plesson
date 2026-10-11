package rea.ohyeahworld.plesson.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createHttpClient(): HttpClient = HttpClient(CIO) {
    applyCommonConfig(defaultApiKey)
}
