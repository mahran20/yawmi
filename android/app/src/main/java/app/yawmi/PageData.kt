package app.yawmi

import android.content.Context
import org.json.JSONObject

/**
 * Data that lives once, in the page (assets/index.html), as single-line JSON constants:
 * `const WORDS = {...};` and `const WHATS_NEW = {...};`. Native code reads the same lines,
 * so the app and its notifications never disagree.
 */
object PageData {
    @Volatile
    private var page: String? = null

    private fun page(ctx: Context): String =
        page ?: ctx.assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }.also { page = it }

    /** The JSON object assigned to `const <name>` on its own line, or null. */
    fun constant(html: String, name: String): JSONObject? {
        val m = Regex("^const $name = (\\{.*\\});$", RegexOption.MULTILINE).find(html) ?: return null
        return try {
            JSONObject(m.groupValues[1])
        } catch (e: Exception) {
            null
        }
    }

    fun constant(ctx: Context, name: String): JSONObject? = try {
        constant(page(ctx), name)
    } catch (e: Exception) {
        null
    }

    /** A du'a opener and a verse or hadith for this prayer, or null if unavailable. */
    fun prayerWord(ctx: Context, prayer: String): Pair<String, String>? = prayerWord(constant(ctx, "WORDS"), prayer)

    fun prayerWord(words: JSONObject?, prayer: String, rnd: kotlin.random.Random = kotlin.random.Random.Default): Pair<String, String>? {
        val w = words ?: return null
        val openers = w.optJSONArray("openers") ?: return null
        val all = w.optJSONArray("words") ?: return null
        val own = mutableListOf<JSONObject>()
        val any = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val e = all.optJSONObject(i) ?: continue
            val p = e.optJSONArray("p")
            if (p == null) any.add(e) else if ((0 until p.length()).any { p.optString(it) == prayer }) own.add(e)
        }
        val pool = if (own.isNotEmpty() && (any.isEmpty() || rnd.nextDouble() < 0.6)) own else any
        if (pool.isEmpty() || openers.length() == 0) return null
        val e = pool[rnd.nextInt(pool.size)]
        return openers.optString(rnd.nextInt(openers.length())) to "${e.optString("t")}\n— ${e.optString("s")}"
    }

    /** Notes for one version, newest wording as written in WHATS_NEW. */
    fun whatsNew(ctx: Context, versionCode: Int): List<String> {
        val arr = constant(ctx, "WHATS_NEW")?.optJSONArray(versionCode.toString()) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }
}
