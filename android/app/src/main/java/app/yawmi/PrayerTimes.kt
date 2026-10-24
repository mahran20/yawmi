package app.yawmi

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer times, Egyptian General Authority of Survey method
 * (Fajr 19.5 deg, Isha 17.5 deg, Shafi'i Asr).
 */
object PrayerTimes {
    val PRAYERS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    val NAMES = mapOf(
        "fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر",
        "maghrib" to "المغرب", "isha" to "العشاء"
    )

    private fun rad(x: Double) = Math.toRadians(x)
    private fun deg(x: Double) = Math.toDegrees(x)
    private fun fix(a: Double, b: Double): Double {
        val r = a % b
        return if (r < 0) r + b else r
    }

    /** [declination, equation of time] */
    private fun sun(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0, 24.0)
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        val eqt = fix(q / 15.0 - ra + 12.0, 24.0) - 12.0
        return doubleArrayOf(decl, eqt)
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Minutes after local midnight for fajr, sunrise, dhuhr, asr, maghrib, isha. */
    fun compute(date: LocalDate, lat: Double, lng: Double, zone: ZoneId): Map<String, Int> {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)

        fun noon(t: Double): Double = 12.0 - sun(jd + t / 24.0)[1]

        fun angleT(a: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t / 24.0)[0]
            val n = noon(t)
            val x = (-sin(rad(a)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            val h = deg(acos(x.coerceIn(-1.0, 1.0))) / 15.0
            return if (ccw) n - h else n + h
        }

        fun asrT(t: Double): Double {
            val decl = sun(jd + t / 24.0)[0]
            val a = -deg(atan(1.0 / (1.0 + tan(rad(abs(lat - decl))))))
            return angleT(a, t, false)
        }

        var fajr = 5.0
        var sunrise = 6.0
        var dhuhr = 12.0
        var asr = 13.0
        var maghrib = 18.0
        var isha = 18.0
        repeat(2) {
            fajr = angleT(19.5, fajr, true)
            sunrise = angleT(0.833, sunrise, true)
            dhuhr = noon(dhuhr)
            asr = asrT(asr)
            maghrib = angleT(0.833, maghrib, false)
            isha = angleT(17.5, isha, false)
        }

        fun mins(v: Double, down: Boolean): Int {
            val h = v + tz - lng / 15.0
            val m = if (down) floor(h * 60.0).toLong() else (h * 60.0).roundToLong()
            return (((m % 1440) + 1440) % 1440).toInt()
        }

        return linkedMapOf(
            "fajr" to mins(fajr, true),
            "sunrise" to mins(sunrise, false),
            "dhuhr" to mins(dhuhr, false),
            "asr" to mins(asr, false),
            "maghrib" to mins(maghrib, false),
            "isha" to mins(isha, false)
        )
    }
}
