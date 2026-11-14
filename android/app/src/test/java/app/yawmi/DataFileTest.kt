package app.yawmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class DataFileTest {
    @get:Rule val tmp = TemporaryFolder()

    private var day = LocalDate.parse("2026-11-14")
    private fun store() = DataFile(tmp.root) { day }
    private fun main() = File(tmp.root, "data.json")
    private fun text(r: DataFile.Read) = (r as DataFile.Read.Ok).obj.getInt("n")

    @Test fun firstRunIsEmpty() {
        assertEquals(DataFile.Read.Empty, store().read())
    }

    @Test fun writesAndReadsBack() {
        assertTrue(store().write("""{"n":1}"""))
        assertEquals(1, text(store().read()))
    }

    @Test fun rejectsAnythingButAJsonObject() {
        store().write("""{"n":1}""")
        for (bad in listOf("", "null", "[1,2]", "{broken", "\"text\"")) {
            assertFalse(bad, store().write(bad))
        }
        assertEquals(1, text(store().read()))
    }

    @Test fun damagedFileFallsBackToTheLastGoodVersion() {
        store().write("""{"n":1}""")
        store().write("""{"n":2}""")
        main().writeText("""{"n":3, "days": {""")   // e.g. the phone died mid-write
        val r = store().read() as DataFile.Read.Ok
        assertEquals(1, r.obj.getInt("n"))
        assertTrue(r.fromBackup)
    }

    @Test fun missingFileFallsBackToBackup() {
        store().write("""{"n":1}""")
        store().write("""{"n":2}""")
        main().delete()
        assertEquals(1, text(store().read()))
    }

    @Test fun bothDamagedIsReportedNotTreatedAsEmpty() {
        store().write("""{"n":1}""")
        store().write("""{"n":2}""")
        main().writeText("garbage")
        File(tmp.root, "data.json.bak").writeText("garbage")
        assertEquals(DataFile.Read.Unreadable, store().read())
    }

    @Test fun leftoverTempFileIsIgnored() {
        store().write("""{"n":1}""")
        File(tmp.root, "data.json.tmp").writeText("""{"n":99""")
        assertEquals(1, text(store().read()))
    }

    @Test fun keepsOneSnapshotPerDayForAWeek() {
        store().write("""{"n":0}""")
        for (i in 1..10) {
            day = day.plusDays(1)
            store().write("""{"n":$i}""")
            store().write("""{"n":${i * 100}}""")   // a second save the same day adds no snapshot
        }
        val snaps = store().snapshots()
        assertEquals(DataFile.KEEP, snaps.size)
        assertEquals("2026-11-24", snaps.first())
        // the snapshot holds the document as it was before that day's first save
        assertEquals(900, org.json.JSONObject(store().snapshot("2026-11-24")!!).getInt("n"))
    }

    @Test fun snapshotNamesAreValidated() {
        assertEquals(null, store().snapshot("../data"))
    }
}
