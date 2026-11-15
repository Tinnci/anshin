package com.driezy.medlog.util

import android.util.Log
import com.driezy.medlog.BuildConfig

/**
 * Centralized logging utility for MedLog.
 *
 * - Verbose, debug, and info logs ([v], [d], [i]) are gated on [BuildConfig.DEBUG] and accept
 *   lazy message lambdas so release builds pay zero string-formatting or allocation cost.
 * - Warning and error logs ([w], [e]) are always emitted and safely no-op when `android.util.Log`
 *   is unmocked in pure JVM unit tests.
 */
object AppLog {
    private const val MAX_TAG_LENGTH = 23

    inline fun v(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            emit(Log.VERBOSE, tag, message(), null)
        }
    }

    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            emit(Log.DEBUG, tag, message(), null)
        }
    }

    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            emit(Log.INFO, tag, message(), null)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        emit(Log.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        emit(Log.ERROR, tag, message, throwable)
    }

    @PublishedApi
    internal fun emit(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val safeTag = normalizeTag(tag)
        try {
            when (priority) {
                Log.VERBOSE -> if (throwable != null) Log.v(safeTag, message, throwable) else Log.v(safeTag, message)
                Log.DEBUG -> if (throwable != null) Log.d(safeTag, message, throwable) else Log.d(safeTag, message)
                Log.INFO -> if (throwable != null) Log.i(safeTag, message, throwable) else Log.i(safeTag, message)
                Log.WARN -> if (throwable != null) Log.w(safeTag, message, throwable) else Log.w(safeTag, message)
                Log.ERROR -> if (throwable != null) Log.e(safeTag, message, throwable) else Log.e(safeTag, message)
            }
        } catch (_: RuntimeException) {
            // Pure JVM unit tests do not mock android.util.Log.
        }
    }

    internal fun normalizeTag(tag: String): String = if (tag.length <= MAX_TAG_LENGTH) tag else tag.take(MAX_TAG_LENGTH)
}
