package com.tomclaw.appsend.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.tomclaw.appsend.BuildConfig
import com.tomclaw.appsend.R
import com.tomclaw.appsend.screen.details.createDetailsActivityIntent
import com.tomclaw.appsend.util.NotificationIconHolder
import com.tomclaw.appsend.util.Analytics
import com.tomclaw.appsend.util.crc32
import com.tomclaw.appsend.util.getColor
import com.tomclaw.imageloader.SimpleImageLoader.imageLoader
import com.tomclaw.imageloader.core.Handlers
import io.reactivex.rxjava3.core.Observable

interface DownloadNotifications {

    fun createInitialNotification(label: String): Notification

    fun subscribe(
        appId: String,
        label: String,
        icon: String?,
        installUri: () -> Uri?,
        stop: () -> Unit,
        observable: Observable<Int>
    )

}

class DownloadNotificationsImpl(
    private val context: Context,
    private val analytics: Analytics,
) : DownloadNotifications {

    private val notificationManager =
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    init {
        with(context.resources) {
            createNotificationChannel(
                channelId = CHANNEL_DOWNLOADING,
                channelName = getString(R.string.downloading_channel_name),
                channelDescription = getString(R.string.downloading_channel_description)
            )
            createNotificationChannel(
                channelId = CHANNEL_INSTALL,
                channelName = getString(R.string.install_channel_name),
                channelDescription = getString(R.string.install_channel_description)
            )
        }
    }

    override fun createInitialNotification(label: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADING)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.waiting_for_download))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setSilent(true)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setColor(getColor(R.color.primary_color, context))
            .setGroup(GROUP_NOTIFICATIONS)
            .build()
    }

    override fun subscribe(
        appId: String,
        label: String,
        icon: String?,
        installUri: () -> Uri?,
        stop: () -> Unit,
        observable: Observable<Int>
    ) {
        val notificationId = appId.crc32()

        val openDetailsIntent = getOpenDetailsIntent(notificationId, appId, label)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_INSTALL)
            .setContentTitle(label)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setSilent(true)
            .setOngoing(true)
            .setColor(getColor(R.color.primary_color, context))
            .setContentIntent(openDetailsIntent)
            .setGroup(GROUP_NOTIFICATIONS)

        val iconHolder = NotificationIconHolder(context.resources, notificationBuilder)
        val handlers = Handlers<NotificationCompat.Builder>()
            .apply {
                successHandler { viewHolder, result ->
                    viewHolder.get().setLargeIcon(result.getDrawable().toBitmap())
                }
            }

        // The holder keeps updating the same builder, so one load is enough —
        // doing it per emission meant up to a hundred loads for one download
        icon?.let { context.imageLoader().load(iconHolder, it, handlers) }

        // takeUntil completes the stream on a terminal status, so the subscription drops
        // itself even when the relay already cached that status before we subscribed
        observable.takeUntil { status ->
            status == ERROR || status == COMPLETED || status == IDLE
        }.subscribe({ status ->
            when (status) {
                AWAIT -> {
                    val notification = notificationBuilder
                        .setContentText(context.getString(R.string.waiting_for_download))
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setProgress(100, 0, true)
                        .setOngoing(true)
                        .build()
                    notificationManager.notify(notificationId, notification)
                }

                ERROR -> {
                    val notification = notificationBuilder
                        .setContentText(context.getString(R.string.download_failed))
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .build()
                    notificationManager.notify(notificationId, notification)
                    stop()
                }

                COMPLETED -> {
                    notificationManager.cancel(notificationId)
                    val uri = installUri()
                    if (uri != null) {
                        val installIntent = getInstallIntent(notificationId, uri)
                        val installNotificationBuilder =
                            NotificationCompat.Builder(context, CHANNEL_INSTALL)
                                .setContentTitle(label)
                                .setContentText(context.getString(R.string.tap_to_install))
                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                .setGroup(GROUP_NOTIFICATIONS)
                                .setOngoing(false)
                                .setAutoCancel(true)
                                .setColor(getColor(R.color.primary_color, context))
                                .setContentIntent(installIntent)
                        val installIconHolder = NotificationIconHolder(
                            resources = context.resources,
                            notificationBuilder = installNotificationBuilder
                        )
                        icon?.run { context.imageLoader().load(installIconHolder, icon, handlers) }
                        val notification = installNotificationBuilder.build()
                        notificationManager.notify(notificationId, notification)
                    }
                    stop()
                }

                IDLE -> {
                    notificationManager.cancel(notificationId)
                    stop()
                }

                STARTED -> {
                    val notification = notificationBuilder
                        .setContentText(context.getString(R.string.waiting_for_download))
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setProgress(100, 0, true)
                        .setOngoing(true)
                        .build()
                    notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
                }

                else -> {
                    val notification = notificationBuilder
                        .setContentText(context.getString(R.string.downloading_progress, status))
                        .setProgress(100, status, false)
                        .build()
                    notificationManager.cancel(notificationId)
                    notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
                }
            }
        }, { error ->
            println("[download notification] Error: $error")
            analytics.trackException(error, mapOf("reason" to "Download status subscription error"))
            notificationManager.cancel(notificationId)
            stop()
        })
    }

    // Extras play no part in PendingIntent identity, so with a shared request
    // code every download resolved to the same one — and CANCEL_CURRENT then
    // killed the intent behind the notification of whatever downloaded before.
    private fun getOpenDetailsIntent(
        requestCode: Int,
        appId: String,
        label: String,
    ): PendingIntent {
        return PendingIntent.getActivity(
            context, requestCode,
            createDetailsActivityIntent(
                context = context,
                appId = appId,
                packageName = null,
                label = label,
                moderation = false,
                finishOnly = false
            ).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )
    }

    private fun getInstallIntent(requestCode: Int, uri: Uri): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        return PendingIntent.getActivity(
            context, requestCode,
            intent,
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel(
        channelId: String,
        channelName: String,
        channelDescription: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(channelId, channelName, importance)
        mChannel.description = channelDescription
        notificationManager.createNotificationChannel(mChannel)
    }

}

const val DOWNLOAD_NOTIFICATION_ID = 1
const val GROUP_NOTIFICATIONS = BuildConfig.APPLICATION_ID + ".NOTIFICATIONS"
const val CHANNEL_DOWNLOADING = "downloading_channel_id"
const val CHANNEL_INSTALL = "install_channel_id"

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
