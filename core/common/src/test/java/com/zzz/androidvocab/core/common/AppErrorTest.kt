package com.zzz.androidvocab.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AppErrorTest {
    @Test
    fun appExceptionUsesUserFacingMessage() {
        val exception = AppException(AppError.ExportFailed("External files directory is unavailable"))

        assertEquals("数据导出失败：External files directory is unavailable", exception.message)
        assertEquals("数据导出失败：External files directory is unavailable", exception.toUserMessage("fallback"))
    }

    @Test
    fun regularExceptionFallsBackToItsMessage() {
        val exception = IllegalStateException("plain failure")

        assertEquals("plain failure", exception.toUserMessage("fallback"))
    }
}
