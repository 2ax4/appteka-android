package com.tomclaw.appsend.core

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import com.tomclaw.appsend.util.logDebug

/**
 * The foreground-service half of a transfer: entering and leaving the
 * foreground, keeping track of what is still running, and the Android 15
 * timeout. What is actually being transferred is up to the subclass.
 */
abstract class TransferService : Service() {

    private val activeTransfers = CopyOnWriteArraySet<String>()

    // Transfer callbacks arrive on the transfer thread; hopping to the main thread keeps
    // them ordered against onStartCommand, so a starting transfer can't race a stopSelf
    protected val handler = Handler(Looper.getMainLooper())

    private var lastStartId: Int = 0

    /** Prefix for this service's log lines. */
    protected abstract val logTag: String

    /** Id of the notification this service stays in the foreground with. */
    protected abstract val notificationId: Int

    /**
     * Posted before the extras are so much as looked at, so it cannot depend
     * on them. The subclass may post a better one later once they are parsed.
     */
    protected abstract fun createInitialNotification(intent: Intent?): Notification

    /** Returns true when the intent actually started a transfer. */
    protected abstract fun onIntentReceived(intent: Intent): Boolean

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId

        // We are launched with startForegroundService(), which gives 5 seconds to enter
        // foreground whatever the intent turns out to hold. Reading extras first would
        // let a malformed intent fall through into ForegroundServiceDidNotStartInTime.
        startForegroundCompat(createInitialNotification(intent))

        val accepted = intent?.let { onIntentReceived(it) } == true
        if (!accepted && activeTransfers.isEmpty()) {
            stopForegroundCompat()
            stopSelf(startId)
        }

        // Nothing useful to redeliver: a restart carries no extras, and re-entering
        // foreground from the background would crash on Android 12+ anyway
        return START_NOT_STICKY
    }

    protected fun trackTransfer(id: String) {
        activeTransfers.add(id)
    }

    // Other transfers may still be running, so don't tear the service down for them.
    protected fun onTransferFinished(id: String) {
        activeTransfers.remove(id)
        if (activeTransfers.isEmpty()) {
            stopForegroundCompat()
            // Keeps a transfer queued right at this moment from being dropped
            stopSelf(lastStartId)
        }
    }

    /**
     * Android 15+ gives a dataSync service 6 hours per day and kills the app with
     * ForegroundServiceDidNotStopInTimeException unless it stops itself within
     * seconds of this callback.
     */
    override fun onTimeout(startId: Int) = onTimeoutReached()

    override fun onTimeout(startId: Int, fgsType: Int) = onTimeoutReached()

    private fun onTimeoutReached() {
        logDebug("[$logTag] onTimeout")
        activeTransfers.clear()
        stopForegroundCompat()
        stopSelf()
    }

    protected fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        logDebug("[$logTag] onDestroy")
        handler.removeCallbacksAndMessages(null)
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        logDebug("[$logTag] onBind")
        return Binder()
    }

}
