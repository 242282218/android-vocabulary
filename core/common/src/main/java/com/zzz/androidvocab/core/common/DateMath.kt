package com.zzz.androidvocab.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun daysBetween(
    start: Instant?,
    end: Instant,
    zoneId: ZoneId,
): Int? {
    if (start == null) {
        return null
    }
    val startDay = LocalDate.ofInstant(start, zoneId)
    val endDay = LocalDate.ofInstant(end, zoneId)
    return ChronoUnit.DAYS
        .between(startDay, endDay)
        .toNonNegativeDayCount()
}

fun daysUntil(
    start: Instant,
    end: Instant,
    zoneId: ZoneId,
): Int {
    val startDay = LocalDate.ofInstant(start, zoneId)
    val endDay = LocalDate.ofInstant(end, zoneId)
    return ChronoUnit.DAYS
        .between(startDay, endDay)
        .toNonNegativeDayCount()
}

fun localLookbackStart(
    now: Instant,
    days: Int,
    zoneId: ZoneId,
): Instant? {
    if (days <= 0) return null
    val today = LocalDate.ofInstant(now, zoneId)
    val startDay = today.minusDaysSaturated((days - 1).toLong())
    return startDay.atStartOfDay(zoneId).toInstant()
}

private fun Long.toNonNegativeDayCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

private fun LocalDate.minusDaysSaturated(days: Long): LocalDate =
    runCatching { minusDays(days) }.getOrDefault(LocalDate.MIN)

private const val MILLIS_PER_MINUTE = 60_000L

fun Long.millisToCompletedMinutes(): Int {
    if (this <= 0L) return 0
    val minutes = ((this - 1L) / MILLIS_PER_MINUTE) + 1L
    return minutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

fun estimateCompletedMinutes(
    itemCount: Int,
    millisPerItem: Long,
): Int {
    if (itemCount <= 0) return 0
    val safeMillisPerItem = millisPerItem.coerceAtLeast(1L)
    val count = itemCount.toLong()
    val totalMillis =
        if (safeMillisPerItem > Long.MAX_VALUE / count) {
            Long.MAX_VALUE
        } else {
            safeMillisPerItem * count
        }
    return totalMillis.millisToCompletedMinutes()
}
