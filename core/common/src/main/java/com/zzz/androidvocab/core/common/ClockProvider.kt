package com.zzz.androidvocab.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface ClockProvider {
    fun now(): Instant

    fun zoneId(): ZoneId

    fun today(): LocalDate = LocalDate.ofInstant(now(), zoneId())

    fun localDay(instant: Instant = now()): String = LocalDate.ofInstant(instant, zoneId()).toString()
}

class SystemClockProvider
    @Inject
    constructor() : ClockProvider {
        private val clock: Clock = Clock.systemDefaultZone()

        override fun now(): Instant = clock.instant()

        override fun zoneId(): ZoneId = clock.zone
    }
