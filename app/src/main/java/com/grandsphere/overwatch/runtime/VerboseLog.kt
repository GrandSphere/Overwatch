package com.grandsphere.overwatch.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Debug trail in Documents/Overwatch/overwatch-verbose.log (same folder as the user alarm log).
 * Breadcrumbs only when [enabled]. Uncaught crashes are always written.
 */
object VerboseLog {
    const val FILE_NAME = "overwatch-verbose.log"
    private const val TAG = "OverwatchVerbose"
    private const val MAX_BYTES = 1_000_000L

    @Volatile
    var enabled: Boolean = false

    @Volatile
    private var appContext: Context? = null

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ow-verbose").apply { isDaemon = true }
    }

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            writeLine(
                always = true,
                level = "E",
                tag = "Crash",
                message = "uncaught on ${thread.name}: ${error.message}",
                error = error,
            )
            writer.shutdown()
            try {
                writer.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun ok(tag: String, message: String) = d(tag, "ok $message")

    fun fail(tag: String, message: String, error: Throwable? = null) {
        writeLine(always = false, level = "E", tag = tag, message = "fail $message", error = error)
    }

    fun d(tag: String, message: String) {
        writeLine(always = false, level = "D", tag = tag, message = message, error = null)
    }

    fun crash(tag: String, message: String, error: Throwable? = null) {
        writeLine(always = true, level = "E", tag = tag, message = message, error = error)
    }

    fun logProcessStart() {
        val ctx = appContext
        d(
            "Process",
            "onCreate sdk=${Build.VERSION.SDK_INT} ${Build.MANUFACTURER} ${Build.MODEL} verbose=$enabled",
        )
        if (ctx != null) d("Log", DocumentsLog.describeTargets(ctx))
    }

    fun resolveExistingFile(context: Context): File? =
        DocumentsLog.resolveExistingNamed(context, FILE_NAME)

    fun ensureShareableCopy(context: Context): File? {
        val src = resolveExistingFile(context) ?: return null
        if (src.length() <= 0L) return null
        val dest = File(context.cacheDir, FILE_NAME)
        src.copyTo(dest, overwrite = true)
        return dest
    }

    private fun writeLine(
        always: Boolean,
        level: String,
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        if (!always && !enabled) return
        val ctx = appContext ?: return
        val time = stamp.format(Date())
        val body = buildString {
            append(time).append(' ').append(level).append(' ').append(tag).append(' ').append(message)
            if (error != null) {
                append('\n')
                append(error.stackTraceToStringTrimmed())
            }
            append('\n')
        }
        when (level) {
            "E" -> Log.e(TAG, "$tag $message", error)
            else -> Log.d(TAG, "$tag $message")
        }
        writer.execute {
            DocumentsLog.writeNamed(ctx, FILE_NAME, body, maxBytes = MAX_BYTES)
        }
    }

    private fun Throwable.stackTraceToStringTrimmed(): String {
        val sw = StringWriter()
        printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd().take(8_000)
    }
}
