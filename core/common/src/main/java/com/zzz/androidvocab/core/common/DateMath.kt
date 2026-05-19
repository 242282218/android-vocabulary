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
        .toInt()
        .coerceAtLeast(0)
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
        .toInt()
        .coerceAtLeast(0)
}
