package app.yawmi

import android.app.Application
import android.content.Context
import java.io.File

/** Process start: installs the crash recorder before any activity, receiver or service runs. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
    }
}

/**
 * Remembers the last crash as "Type at File.kt:line" (no messages, no user data) so the app can
 * count it anonymously on the next launch, if the user allows usage stats.
 */
object Crash {
    private const val FILE = "last-crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                File(app.filesDir, FILE).writeText(describe(e))
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Innermost cause, located at the first frame inside the app's own code. */
    fun describe(e: Throwable): String {
        var root = e
        while (root.cause != null && root.cause !== root) root = root.cause!!
        val frame = root.stackTrace.firstOrNull { it.className.startsWith("app.yawmi") } ?: root.stackTrace.firstOrNull()
        val where = frame?.let { "${it.fileName}:${it.lineNumber}" } ?: "unknown"
        return "${root.javaClass.simpleName} at $where"
    }

    fun take(ctx: Context): String {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return ""
        val s = try {
            f.readText()
        } catch (e: Exception) {
            ""
        }
        f.delete()
        return s.take(120)
    }
}
