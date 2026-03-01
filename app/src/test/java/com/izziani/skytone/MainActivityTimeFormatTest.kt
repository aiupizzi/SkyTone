package com.izziani.skytone

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class MainActivityTimeFormatTest {

    @Test
    fun formatUtcToLocalTime_convertsZuluTimeToRequestedZone() {
        val result = formatUtcToLocalTime(
            utcTime = "2024-07-10T18:30:00Z",
            zoneId = ZoneId.of("America/New_York"),
            locale = Locale.US
        )

        assertEquals("02:30 PM", result)
    }

    @Test
    fun formatUtcToLocalTime_convertsOffsetUtcStringToRequestedZone() {
        val result = formatUtcToLocalTime(
            utcTime = "2024-07-10T18:30:00+00:00",
            zoneId = ZoneId.of("Asia/Kolkata"),
            locale = Locale.US
        )

        assertEquals("12:00 AM", result)
    }

    @Test
    fun formatUtcToLocalTime_returnsErrorForInvalidInput() {
        val result = formatUtcToLocalTime("not-a-date", ZoneId.of("UTC"), Locale.US)

        assertEquals("Error converting time", result)
    }
}
