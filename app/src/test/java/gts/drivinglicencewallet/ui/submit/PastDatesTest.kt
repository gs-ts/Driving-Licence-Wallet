package gts.drivinglicencewallet.ui.submit

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PastDatesTest {

    private val defaultTimeZone = TimeZone.getDefault()
    private val today = LocalDate.of(2026, 11, 9)

    @AfterTest
    fun restoreTimeZone() = TimeZone.setDefault(defaultTimeZone)

    /**
     * The picker passes each date as its midnight in UTC. West of UTC, that
     * time is still the day before, so a check in the device's time zone would
     * allow tomorrow.
     */
    @Test
    fun `given a device west of UTC, when the date picker checks today and tomorrow, then only today can be picked`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val pastDates = PastDates(today = today)

        assertTrue(actual = pastDates.isSelectableDate(utcTimeMillis = today.pickerMillis()))
        assertFalse(actual = pastDates.isSelectableDate(utcTimeMillis = today.plusDays(1).pickerMillis()))
    }

    /** The time that the date picker passes for this date, which is its midnight in UTC. */
    private fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
