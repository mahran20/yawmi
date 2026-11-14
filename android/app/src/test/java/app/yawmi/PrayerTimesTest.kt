package app.yawmi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The alarm (Kotlin) and the screen (JavaScript) compute prayer times separately; they must agree
 * to the minute. Expected values come from the web app's calcTimes, which was checked against the
 * published Cairo timetable (Egyptian General Authority of Survey).
 */
class PrayerTimesTest {
    private val cairo = ZoneId.of("Africa/Cairo")

    private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun check(date: String, lat: Double, lng: Double, expected: String) {
        val t = PrayerTimes.compute(LocalDate.parse(date), lat, lng, cairo)
        val got = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").joinToString(" ") { hm(t.getValue(it)) }
        assertEquals("$date ($lat, $lng)", expected, got)
    }

    @Test fun cairoWinter() = check("2026-01-15", 30.0444, 31.2357, "05:20 06:52 12:04 14:58 17:17 18:39")
    @Test fun cairoBeforeSummerTime() = check("2026-04-20", 30.0444, 31.2357, "03:51 05:23 11:54 15:30 18:25 19:47")
    @Test fun cairoSummerTime() = check("2026-06-21", 30.0444, 31.2357, "04:08 05:54 12:57 16:32 19:59 21:33")
    @Test fun cairoAutumn() = check("2026-11-14", 30.0444, 31.2357, "04:49 06:19 11:39 14:39 16:59 18:20")
    @Test fun alexandria() = check("2026-06-21", 31.2001, 29.9187, "04:08 05:57 13:02 16:41 20:07 21:43")
    @Test fun aswan() = check("2026-12-21", 24.0889, 32.8998, "04:59 06:27 11:46 14:46 17:06 18:25")

    @Test fun timesAreInOrder() {
        var d = LocalDate.parse("2026-01-01")
        repeat(366) {
            val t = PrayerTimes.compute(d, 30.0444, 31.2357, cairo)
            val seq = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").map { t.getValue(it) }
            assertEquals("order on $d", seq.sorted(), seq)
            d = d.plusDays(1)
        }
    }
}
