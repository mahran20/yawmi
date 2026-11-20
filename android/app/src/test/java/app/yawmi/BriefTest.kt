package app.yawmi

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class BriefTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val day = LocalDate.parse("2026-11-22") // Sunday

    private fun at(date: String, hm: String) = java.time.LocalDateTime.parse("${date}T$hm").atZone(zone).toInstant().toEpochMilli()

    private fun data(): JSONObject {
        val items = JSONArray()
            .put(JSONObject().put("id", "quran").put("label", "ورد القرآن"))
            .put(JSONObject().put("id", "read").put("label", "قراية"))
            .put(JSONObject().put("id", "gym").put("label", "تمرين").put("sched", JSONObject().put("type", "weekly").put("times", 3)))
        val work = JSONObject().put("id", "w").put("sched", JSONObject().put("type", "days").put("days", JSONArray(listOf(0, 1, 2, 3, 4))))
            .put("items", JSONArray().put(JSONObject().put("id", "focus").put("label", "ساعتين تركيز")))
        val tasks = JSONObject()
            .put("m", JSONObject().put("text", "ميتنج الفريق").put("kind", "meeting").put("due", at("2026-11-22", "11:00")))
            .put("r", JSONObject().put("text", "تجديد الرخصة").put("kind", "errand").put("due", at("2026-11-20", "09:00")).put("dateOnly", true))
            .put("b", JSONObject().put("text", "كلم البنك").put("kind", "call").put("due", at("2026-11-23", "10:30")))
            .put("x", JSONObject().put("text", "اشتري شاحن"))
            .put("d", JSONObject().put("text", "حاجة خلصت").put("done", true).put("due", at("2026-11-22", "12:00")))
        return JSONObject()
            .put("settings", JSONObject().put("name", "مهران"))
            .put("groups", JSONArray().put(JSONObject().put("id", "g").put("items", items)).put(work))
            .put("days", JSONObject().put("2026-11-22", JSONObject().put("checks", JSONObject().put("quran", true))))
            .put("tasks", tasks)
    }

    @Test fun morningListsTodayLateAndHabits() {
        val n = Brief.morning(data(), day, zone)
        assertEquals("صباح الخير يا مهران · عندك ميعاد النهارده", n.title)
        assertTrue(n.text, n.text.contains("📅 ميتنج الفريق ١١:٠٠ ص"))
        assertTrue(n.text.contains("⏳ متأخر: تجديد الرخصة"))
        assertTrue(n.text.contains("📝 تاسك واحد من غير معاد"))
        assertTrue(n.text.contains("🌱 ٣ عادات النهارده"))      // weekly gym not counted
        assertFalse(n.text.contains("حاجة خلصت"))
    }

    @Test fun eveningNamesWhatIsLeftAndTomorrow() {
        val n = Brief.evening(data(), day, zone)
        assertEquals("فاضلك ٢ من ٣", n.title)
        assertTrue(n.text, n.text.contains("لسه: قراية، ساعتين تركيز"))
        assertTrue(n.text.contains("تجديد الرخصة (متأخر)"))
        assertTrue(n.text.contains("بكرة: 📞 كلم البنك ١٠:٣٠ ص"))
    }

    @Test fun workItemsSkipTheWeekend() {
        val sat = LocalDate.parse("2026-11-21")
        assertTrue(Brief.morning(data(), sat, zone).text.contains("🌱 ٢ عادات"))
    }

    @Test fun emptyDayStillSaysSomething() {
        val n = Brief.morning(JSONObject(), day, zone)
        assertEquals("صباح الخير", n.title)
        assertTrue(n.text.isNotBlank())
        assertEquals("تصبح على خير.", Brief.evening(JSONObject(), day, zone).text)
    }

    @Test fun tipsGoInOrderAndOnlyOnce() {
        val d = JSONObject().put("settings", JSONObject().put("alarm", JSONObject().put("enabled", true)))
        assertEquals("code", Tips.next(d, emptySet(), false)!!.id)
        assertEquals("widget", Tips.next(d, setOf("code"), false)!!.id)
        assertEquals("tasks", Tips.next(d, setOf("code"), true)!!.id)
        assertNull(Tips.next(d.put("tasks", JSONObject().put("a", JSONObject())), setOf("code"), true))
    }

    @Test fun pageConstantsAreReadable() {
        val html = File("src/main/assets/index.html").readText()
        val words = PageData.constant(html, "WORDS")
        assertNotNull(words)
        val w = PageData.prayerWord(words, "fajr", kotlin.random.Random(1))
        assertNotNull(w)
        assertTrue(w!!.second.contains("\n— "))
        assertNotNull(PageData.constant(html, "WHATS_NEW"))
    }
}
