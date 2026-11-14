package app.yawmi

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * The app's single JSON document on disk, written so that a crash, a full disk or a bad
 * save can never lose the user's history.
 *
 * - Every write goes to a temp file, is flushed to disk, then renamed over the real file.
 * - The previous good version is kept as `data.json.bak`; reads fall back to it if the main
 *   file is missing or unreadable.
 * - Once a day the document is copied to `snapshots/<date>.json` (last [KEEP] days kept), so a
 *   bug or a mistaken reset can be undone from Settings.
 * - Only a JSON object is ever written.
 *
 * Plain JVM code (no Android types) so it can be unit tested.
 */
class DataFile(private val dir: File, private val today: () -> LocalDate = { LocalDate.now() }) {
    private val main = File(dir, "data.json")
    private val bak = File(dir, "data.json.bak")
    private val tmp = File(dir, "data.json.tmp")
    private val snaps = File(dir, "snapshots")

    sealed class Read {
        /** No data saved yet (first run). */
        object Empty : Read()
        data class Ok(val text: String, val obj: JSONObject, val fromBackup: Boolean) : Read()
        /** Data exists but neither the file nor its backup can be parsed. Never overwrite it. */
        object Unreadable : Read()
    }

    private fun parse(f: File): JSONObject? =
        try {
            if (f.isFile && f.length() > 0) JSONObject(f.readText()) else null
        } catch (e: Exception) {
            null
        }

    fun read(): Read {
        parse(main)?.let { return Read.Ok(it.toString(), it, false) }
        parse(bak)?.let { return Read.Ok(it.toString(), it, true) }
        return if ((main.isFile && main.length() > 0) || (bak.isFile && bak.length() > 0)) Read.Unreadable else Read.Empty
    }

    /** Writes [json] if it is a JSON object. Returns false (and changes nothing) otherwise. */
    fun write(json: String): Boolean {
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            return false
        }
        snapshotIfNeeded()
        FileOutputStream(tmp).use { out ->
            out.write(obj.toString().toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        // keep the current file as the backup only if it is itself readable
        if (parse(main) != null) {
            bak.delete()
            main.renameTo(bak)
        }
        if (!tmp.renameTo(main)) {
            tmp.copyTo(main, overwrite = true)
            tmp.delete()
        }
        return true
    }

    /** Before the first write of a day, keep a copy of the document as it was. */
    private fun snapshotIfNeeded() {
        val day = today().toString()
        val target = File(snaps, "$day.json")
        if (target.exists()) return
        val current = parse(main) ?: return
        snaps.mkdirs()
        target.writeText(current.toString())
        snapshots().drop(KEEP).forEach { File(snaps, "$it.json").delete() }
    }

    /** Snapshot dates, newest first. */
    fun snapshots(): List<String> =
        (snaps.listFiles() ?: emptyArray())
            .map { it.name }
            .filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}\.json""")) }
            .map { it.removeSuffix(".json") }
            .sortedDescending()

    fun snapshot(date: String): String? {
        if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return null
        return parse(File(snaps, "$date.json"))?.toString()
    }

    companion object {
        const val KEEP = 7
    }
}
