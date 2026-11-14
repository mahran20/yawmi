package app.yawmi

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SchedTest {
    private val sat = LocalDate.parse("2026-11-14")   // a Saturday
    private val sun = LocalDate.parse("2026-11-15")
    private val fri = LocalDate.parse("2026-11-20")

    private fun days(vararg d: Int) = JSONObject().put("type", "days").put("days", JSONArray(d.toList()))
    private fun weekly(n: Int) = JSONObject().put("type", "weekly").put("times", n)

    @Test fun sundayIsZeroLikeJavaScript() {
        assertEquals(6, Sched.dow(sat))
        assertEquals(0, Sched.dow(sun))
        assertEquals(5, Sched.dow(fri))
    }

    @Test fun dailyByDefault() {
        assertTrue(Sched.scheduledOn(null, sat))
        assertTrue(Sched.scheduledOn(null, fri))
    }

    @Test fun workDaysSkipTheWeekend() {
        val work = days(0, 1, 2, 3, 4)
        assertTrue(Sched.scheduledOn(work, sun))
        assertFalse(Sched.scheduledOn(work, sat))
        assertFalse(Sched.scheduledOn(work, fri))
    }

    @Test fun weeklyItemsAreAnyDay() {
        assertTrue(Sched.isWeekly(weekly(3)))
        assertFalse(Sched.isWeekly(days(1)))
    }

    @Test fun itemScheduleWinsOverSection() {
        val g = JSONObject().put("sched", days(0, 1, 2, 3, 4))
        val own = JSONObject().put("sched", weekly(2))
        val inherit = JSONObject()
        assertTrue(Sched.isWeekly(Sched.of(g, own)))
        assertFalse(Sched.scheduledOn(Sched.of(g, inherit), sat))
    }

    @Test fun weekCountStartsOnTheChosenDay() {
        val data = JSONObject().put("days", JSONObject()
            .put("2026-11-13", JSONObject().put("checks", JSONObject().put("gym", true)))   // Friday
            .put("2026-11-14", JSONObject().put("checks", JSONObject().put("gym", true)))   // Saturday
            .put("2026-11-16", JSONObject().put("checks", JSONObject().put("gym", true))))  // Monday
        assertEquals(2, Sched.weekCount(data, "gym", LocalDate.parse("2026-11-17"), 6)) // week from Saturday
        assertEquals(1, Sched.weekCount(data, "gym", LocalDate.parse("2026-11-17"), 0)) // week from Sunday
    }

    @Test fun dayEndsAtThreeAm() {
        assertEquals("2026-11-13", Store.logicalKey(LocalDateTime.parse("2026-11-14T02:59")))
        assertEquals("2026-11-14", Store.logicalKey(LocalDateTime.parse("2026-11-14T03:00")))
    }
}
