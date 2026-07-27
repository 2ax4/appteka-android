package com.tomclaw.appsend.util

import android.util.Log

/**
 * For the diagnostics scattered through transfers and storage, where threading
 * a [Logger] through would mean reshaping constructors that have no other
 * reason to change. Goes to logcat under one tag, so it can be filtered and
 * stripped — unlike println, which writes to stdout in release builds too.
 */
fun logDebug(message: String) {
    Log.d(LOG_TAG, message)
}

interface Logger {

    fun log(message: String)

    fun log(message: String, ex: Throwable)

}

class LoggerImpl : Logger {

    override fun log(message: String) {
        Log.d(LOG_TAG, message)
    }

    override fun log(message: String, ex: Throwable) {
        Log.d(LOG_TAG, message, ex)
    }

}

private const val LOG_TAG = "appteka"
