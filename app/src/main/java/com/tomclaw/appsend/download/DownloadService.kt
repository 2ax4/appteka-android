package com.tomclaw.appsend.download

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.tomclaw.appsend.R
import com.tomclaw.appsend.appComponent
import com.tomclaw.appsend.core.TransferService
import com.tomclaw.appsend.download.di.DownloadServiceModule
import javax.inject.Inject

class DownloadService : TransferService() {

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var notifications: DownloadNotifications

    override val logTag = "download service"

    override val notificationId = DOWNLOAD_NOTIFICATION_ID

    override fun onCreate() {
        super.onCreate()
        println("[download service] onCreate")
        appComponent
            .downloadServiceComponent(DownloadServiceModule(this))
            .inject(service = this)
    }

    override fun createInitialNotification(intent: Intent?): Notification {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: getString(R.string.app_name)
        return notifications.createInitialNotification(label)
    }

    override fun onIntentReceived(intent: Intent): Boolean {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: return false
        val version = intent.getStringExtra(EXTRA_VERSION) ?: return false
        val icon = intent.getStringExtra(EXTRA_ICON)
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return false
        val url = intent.getStringExtra(EXTRA_URL) ?: return false
        val sha1 = intent.getStringExtra(EXTRA_SHA1)

        println("[download service] onStartCommand(label = $label, version = $version, appId = $appId, url = $url)")

        trackTransfer(appId)

        val relay = downloadManager.status(appId)

        downloadManager.download(label, version, appId, url, sha1)

        notifications.subscribe(
            appId = appId,
            label = label,
            icon = icon,
            installUri = {
                downloadManager.getInstallUri(label, version, appId)
            },
            stop = {
                handler.post { onTransferFinished(appId) }
            },
            observable = relay,
        )
        return true
    }

}

fun createDownloadIntent(
    context: Context,
    label: String,
    version: String,
    icon: String?,
    appId: String,
    url: String,
    sha1: String?,
): Intent = Intent(context, DownloadService::class.java)
    .putExtra(EXTRA_LABEL, label)
    .putExtra(EXTRA_VERSION, version)
    .putExtra(EXTRA_ICON, icon)
    .putExtra(EXTRA_APP_ID, appId)
    .putExtra(EXTRA_URL, url)
    .putExtra(EXTRA_SHA1, sha1)

private const val EXTRA_LABEL = "label"
private const val EXTRA_VERSION = "version"
private const val EXTRA_ICON = "icon"
private const val EXTRA_APP_ID = "app_id"
private const val EXTRA_URL = "url"
private const val EXTRA_SHA1 = "sha1"
