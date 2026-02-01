package com.veillink.vpn.common.network

import com.veillink.vpn.common.model.ApiError
import com.veillink.vpn.common.model.ApiException
import com.veillink.vpn.common.model.ErrorResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.json.Json

/**
 * Общий API-клиент, не завязанный на Android.
 * Работает поверх Ktor HttpClient (который даёт HttpClientFactory).
 */
class ApiService(
    private val client: HttpClient
) {
    // общий хелпер
    private suspend inline fun <T> safeCall(
        crossinline request: suspend () -> HttpResponse,
        crossinline mapSuccess: suspend (HttpResponse) -> T
    ): T {
        try {
            val response = request()

            if (response.status.isSuccess()) {
                return mapSuccess(response)
            }

            // HTTP ошибка → читаем тело и парсим как на фронте
            val raw = response.bodyAsText()
            val apiError = parseServerError(raw)
            throw ApiException(apiError)

        } catch (ioe: IOException) {
            // 1. Вообще нет ответа от сервера
            val apiError = ApiError(
                message = "Не удалось подключиться к серверу. Проверьте соединение или статус сервера.",
                errorCode = "NETWORK"
            )
            throw ApiException(apiError, ioe)

        } catch (e: ApiException) {
            // уже упакованная API-ошибка – просто пробрасываем
            throw e

        } catch (e: Exception) {
            // что-то совсем странное: парсер упал, баг и т.п.
            val apiError = ApiError(
                message = e.message ?: "Что-то пошло не так. Попробуйте позже.",
                errorCode = "UNEXPECTED"
            )
            throw ApiException(apiError, e)
        }
    }

    private val errorJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun parseServerError(raw: String): ApiError {
        val trimmed = raw.trim()
        val fallback = "Что-то пошло не так. Попробуйте позже."

        if (trimmed.isEmpty()) {
            return ApiError(fallback, errorCode = "UNEXPECTED")
        }

        return try {
            val dto = errorJson.decodeFromString<ErrorResponseDto>(trimmed)
            val msg = when (dto.errorCode) {
                "UNEXPECTED" -> fallback
                else -> dto.message ?: fallback
            }
            ApiError(msg, dto.errorCode)
        } catch (e: Exception) {
            println("ERR_RAW decode failed: ${e::class.simpleName}: ${e.message}")
            println("ERR_RAW body='$trimmed'")
            // не JSON – считаем, что сервер прислал просто текст
            ApiError(trimmed, null)
        }
    }

    /**
     * Логин. Возвращает JWT или null, если логин не удался.
     */
    suspend fun login(email: String, password: String): String =
        safeCall(
            request = {
                client.post("/login") {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("email", email)
                                append("password", password)
                            }
                        )
                    )
                }
            },
            mapSuccess = { response ->
                val token = response.bodyAsText().trim()
                if (token.isEmpty()) {
                    throw ApiException(
                        ApiError("Пустой ответ от сервера.", "UNEXPECTED")
                    )
                }
                token
            }
        )
}
