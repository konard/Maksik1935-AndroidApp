package com.veillink.vpn.common.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val message: String? = null,
    val errorCode: String? = null
)

data class ApiError(
    val message: String,
    val errorCode: String? = null
)

class ApiException(
    val apiError: ApiError,
    cause: Throwable? = null
) : Exception(apiError.message, cause)