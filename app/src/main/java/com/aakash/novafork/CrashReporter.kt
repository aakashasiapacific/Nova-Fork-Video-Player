package com.aakash.novafork

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Keeps the last crash in `filesDir/last_crash.txt` so the next start can offer to share it.
 * A sideloaded app gets no crash reports from the store, so this is the only way to learn
 * what went wrong on someone's phone.
 *
 * [log] keeps a short in-memory trail of what the app did last; it goes into the report.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val REPORT_FILE = "last_crash.txt"
    private const val BREADCRUMB_LIMIT = 30
    private const val MAX_STACK_CHARS = 100_000
    private const val MAX_REPORT_CHARS = 200_000

    private val breadcrumbs = ArrayDeque<String>(BREADCRUMB_LIMIT)
    private val breadcrumbTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var installed = false

    /** Wraps the default uncaught-exception handler. Call first thing in Application.onCreate. */
    fun install(context: Context) {
        val app = context.applicationContext ?: context
        synchronized(this) {
            if (installed) return
            installed = true
        }
        // Resolved now: at crash time the fewer framework calls, the better.
        val reportFile = File(app.filesDir, REPORT_FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(reportFile, thread, error)
            } catch (t: Throwable) {
                // Best effort: the crash itself must still reach the system below.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        log("Started ${versionLabel()}")
    }

    /** The report of a crash the user has not seen yet, or null. */
    fun pending(context: Context): String? =
        try {
            val file = reportFile(context)
            if (file.isFile) file.readText().takeIf { it.isNotBlank() } else null
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the last crash report", e)
            null
        }

    /** Forgets the last report (shared or dismissed). */
    fun clear(context: Context) {
        try {
            reportFile(context).delete()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't delete the last crash report", e)
        }
    }

    /** Adds a line to the trail that goes into the next crash report. Cheap; any thread. */
    fun log(message: String, error: Throwable? = null) {
        val detail = if (error == null) "" else ": ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        synchronized(breadcrumbs) {
            if (breadcrumbs.size >= BREADCRUMB_LIMIT) breadcrumbs.removeFirst()
            breadcrumbs.addLast("${breadcrumbTime.format(Date())} $message$detail")
        }
    }

    private fun reportFile(context: Context): File = File(context.applicationContext.filesDir, REPORT_FILE)

    private fun write(file: File, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString().take(MAX_STACK_CHARS)
        val trail = synchronized(breadcrumbs) { breadcrumbs.toList() }
        val report = buildString {
            appendLine("Nova Fork crash report")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("App: ${versionLabel()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack.trimEnd())
            appendLine()
            if (trail.isNotEmpty()) {
                appendLine()
                appendLine("Last events:")
                trail.forEach { appendLine(it) }
            }
        }.take(MAX_REPORT_CHARS)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "$REPORT_FILE.tmp")
        temp.writeText(report)
        if (!temp.renameTo(file)) {
            file.writeText(report)
            temp.delete()
        }
    }

    private fun versionLabel(): String =
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"
}
