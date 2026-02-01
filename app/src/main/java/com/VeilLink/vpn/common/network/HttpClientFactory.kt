package com.veillink.vpn.common.network

import com.veillink.vpn.common.model.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * KMP-ядро выполнения http-запросов
 */
class HttpClientFactory(
    private val baseUrl: String,
    private val tokenStore: TokenStore,                // твой интерфейс с suspend-методами
    private val engineFactory: HttpClientEngineFactory<*>, // CIO сейчас, потом Darwin и т.п.
    private val onAuthLost: () -> Unit = {}            // дергаем, когда токен окончательно умер
) {
    private val refreshMutex = Mutex()

    fun create(): HttpClient {
        // 1. Создаём клиент с общей конфигурацией
        val client = HttpClient(engineFactory) {
            expectSuccess = false

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }

            // только базовый URL, без токена
            defaultRequest {
                url(baseUrl)
            }
        }

        // 2. Вешаем перехватчик на HttpSend - здесь уже можно вызывать suspend
        client.plugin(HttpSend).intercept { request ->
            val url = request.url.encodedPath // получение чистого url, куда идет запрос

            // Для /login, /register, /renewToken:
            // - не подставляем Authorization
            // - не пытаемся renew
            if (url.endsWith("/login") || url.endsWith("/register") || url.endsWith("/renewToken")) {
                return@intercept execute(request)
            }
            // 2.1. Перед отправкой подставляем актуальный токен
            tokenStore.getAccessToken()?.let { token ->
                request.headers.remove(HttpHeaders.Authorization)
                request.header(HttpHeaders.Authorization, "Bearer $token")
            }

            // 2.2. Отправляем запрос первый раз
            val originalCall = execute(request)

            if (originalCall.response.status != HttpStatusCode.Unauthorized) {
                // не 401 — возвращаем как есть
                return@intercept originalCall
            }

            // 2.3. 401 — пробуем один раз сделать renew (single-flight)
            val newToken = refreshMutex.withLock {
                refreshToken()
            }

            if (newToken == null) {
                // renew не вышел — считаем, что разлогинились
                tokenStore.clear()
                onAuthLost()
                return@intercept originalCall
            }

            // 2.4. Обновляем заголовок и повторяем запрос
            request.headers.remove(HttpHeaders.Authorization)
            request.header(HttpHeaders.Authorization, "Bearer $newToken")

            execute(request)
        }

        return client
    }

    /**
     * Обновляем токен по текущему JWT (MVP: access == refresh).
     * Возвращаем новый токен или null, если renew не удался.
     */
    private suspend fun refreshToken(): String? {
        val current = tokenStore.getAccessToken() ?: return null

        val authClient = HttpClient(engineFactory) {
            expectSuccess = false
            defaultRequest { url(baseUrl) }
        }

        return try {
            val response = authClient.post("/renewToken") {
                header(HttpHeaders.Authorization, "Bearer $current")
            }

            if (!response.status.isSuccess()) {
                null
            } else {
                val newToken = response.bodyAsText().trim().ifEmpty { null }
                tokenStore.setAccessToken(newToken)
                newToken
            }
        } catch (_: Throwable) {
            null
        } finally {
            authClient.close()
        }
    }
}