package com.zzz.androidvocab.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DateMathTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun daysBetweenClampsNegativeAndHugeRanges() {
        val start = LocalDate.of(2026, 5, 16).atStartOfDay(utc).toInstant()
        val sameDay = LocalDate.of(2026, 5, 16).atStartOfDay(utc).toInstant()
        val previousDay = LocalDate.of(2026, 5, 15).atStartOfDay(utc).toInstant()
        val farFuture = LocalDate.of(6_000_000, 1, 1).atStartOfDay(utc).toInstant()

        assertNull(daysBetween(start = null, end = sameDay, zoneId = utc))
        assertEquals(0, daysBetween(start, sameDay, utc))
        assertEquals(0, daysBetween(start, previousDay, utc))
        assertEquals(Int.MAX_VALUE, daysBetween(start, farFuture, utc))
    }

    @Test
    fun daysUntilClampsNegativeAndHugeRanges() {
        val start = LocalDate.of(2026, 5, 16).atStartOfDay(utc).toInstant()
        val previousDay = LocalDate.of(2026, 5, 15).atStartOfDay(utc).toInstant()
        val farFuture = LocalDate.of(6_000_000, 1, 1).atStartOfDay(utc).toInstant()

        assertEquals(0, daysUntil(start, previousDay, utc))
        assertEquals(Int.MAX_VALUE, daysUntil(start, farFuture, utc))
    }

    @Test
    fun localLookbackStartUsesInclusiveLocalCalendarDays() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val now =
            LocalDate
                .of(2026, 5, 17)
                .atTime(0, 30)
                .atZone(shanghai)
                .toInstant()

        assertNull(localLookbackStart(now, days = 0, zoneId = shanghai))
        assertEquals(
            LocalDate.of(2026, 5, 17).atStartOfDay(shanghai).toInstant(),
            localLookbackStart(now, days = 1, zoneId = shanghai),
        )
        assertEquals(
            LocalDate.of(2026, 5, 16).atStartOfDay(shanghai).toInstant(),
            localLookbackStart(now, days = 2, zoneId = shanghai),
        )
    }

    @Test
    fun millisToCompletedMinutesRoundsUpAndClampsLargeValues() {
        assertEquals(0, 0L.millisToCompletedMinutes())
        assertEquals(1, 1L.millisToCompletedMinutes())
        assertEquals(1, 60_000L.millisToCompletedMinutes())
        assertEquals(2, 60_001L.millisToCompletedMinutes())
        assertEquals(Int.MAX_VALUE, Long.MAX_VALUE.millisToCompletedMinutes())
    }

    @Test
    fun estimateCompletedMinutesSaturatesBeforeMultiplicationOverflow() {
        assertEquals(0, estimateCompletedMinutes(itemCount = 0, millisPerItem = 30_000L))
        assertEquals(1, estimateCompletedMinutes(itemCount = 2, millisPerItem = -10L))
        assertEquals(2, estimateCompletedMinutes(itemCount = 20, millisPerItem = 3_500L))
        assertEquals(Int.MAX_VALUE, estimateCompletedMinutes(itemCount = Int.MAX_VALUE, millisPerItem = Long.MAX_VALUE))
    }
}
