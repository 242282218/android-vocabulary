package com.zzz.androidvocab.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class SystemClockProviderTest {
    @Test
    fun zoneIdReadsCurrentSystemDefaultEveryTime() {
        val original = TimeZone.getDefault()
        val provider = SystemClockProvider()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(ZoneId.of("UTC"), provider.zoneId())

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            assertEquals(ZoneId.of("Asia/Shanghai"), provider.zoneId())
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
