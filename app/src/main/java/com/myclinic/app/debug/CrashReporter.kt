package com.myclinic.app.debug

import android.content.Context
import android.os.Build
import com.myclinic.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * TEST BUILDS ONLY. Saves the error of a crash to a private file, so the
 * next launch can show it on screen (see LaunchActivity) and the doctor can
 * copy it to the developer without needing Android Studio.
 * Release builds never install this.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        if (!BuildConfig.DEBUG) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(appContext.filesDir, FILE).writeText(
                    buildString {
                        appendLine("My Clinic ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) crashed")
                        appendLine("Time: ${Instant.now()}")
                        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(trace.take(20_000))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
