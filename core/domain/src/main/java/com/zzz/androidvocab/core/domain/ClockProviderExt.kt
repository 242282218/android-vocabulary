package com.zzz.androidvocab.core.domain

import com.zzz.androidvocab.core.common.ClockProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate

internal const val TIME_REFRESH_INTERVAL_MS = 60_000L
internal const val AVERAGE_DURATION_DAYS = 7
internal const val DEFAULT_REVIEW_CARD_DURATION_MS = 8_000L
internal const val MILLIS_PER_MINUTE = 60_000L

internal fun estimateRemainingMinutes(
    remainingCount: Int,
    averageDurationMs: Long?,
): Int {
    if (remainingCount <= 0) return 0
    val cardDurationMs = averageDurationMs ?: DEFAULT_REVIEW_CARD_DURATION_MS
    val totalDurationMs = remainingCount.toLong() * cardDurationMs.coerceAtLeast(1L)
    return ((totalDurationMs + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

internal fun ClockProvider.observeNow(): Flow<Instant> =
    flow {
        while (currentCoroutineContext().isActive) {
            emit(now())
            delay(TIME_REFRESH_INTERVAL_MS)
        }
    }

internal fun ClockProvider.observeToday(): Flow<LocalDate> = observeNow().map { localDate(it) }.distinctUntilChanged()

internal fun ClockProvider.localDate(now: Instant): LocalDate = LocalDate.ofInstant(now, zoneId())
