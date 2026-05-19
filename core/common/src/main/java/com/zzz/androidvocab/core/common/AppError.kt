package com.zzz.androidvocab.core.common

sealed interface AppError {
    data class VocabularyImportFailed(
        val reason: String,
    ) : AppError

    data class DatabaseWriteFailed(
        val reason: String,
    ) : AppError

    data class SchedulerFailed(
        val reason: String,
    ) : AppError

    data class ExportFailed(
        val reason: String,
    ) : AppError
}

val AppError.userMessage: String
    get() =
        when (this) {
            is AppError.VocabularyImportFailed -> "词库导入失败：$reason"
            is AppError.DatabaseWriteFailed -> "学习数据保存失败：$reason"
            is AppError.SchedulerFailed -> "复习计划计算失败：$reason"
            is AppError.ExportFailed -> "数据导出失败：$reason"
        }

class AppException(
    val error: AppError,
    override val cause: Throwable? = null,
) : RuntimeException(error.userMessage, cause)

fun Throwable.toUserMessage(fallback: String): String =
    when (this) {
        is AppException -> error.userMessage
        else -> message ?: fallback
    }
