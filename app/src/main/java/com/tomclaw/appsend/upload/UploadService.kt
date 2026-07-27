package com.tomclaw.appsend.upload

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.tomclaw.appsend.R
import com.tomclaw.appsend.appComponent
import com.tomclaw.appsend.core.TransferService
import com.tomclaw.appsend.upload.di.UploadServiceModule
import com.tomclaw.appsend.util.getParcelableExtraCompat
import javax.inject.Inject

class UploadService : TransferService() {

    @Inject
    lateinit var uploadManager: UploadManager

    @Inject
    lateinit var notifications: UploadNotifications

    override val logTag = "upload service"

    override val notificationId = UPLOAD_NOTIFICATION_ID

    override fun onCreate() {
        super.onCreate()
        println("[upload service] onCreate")
        appComponent
            .uploadServiceComponent(UploadServiceModule(this))
            .inject(service = this)
    }

    override fun createInitialNotification(intent: Intent?): Notification {
        return notifications.createInitialNotification(getString(R.string.app_name))
    }

    override fun onIntentReceived(intent: Intent): Boolean {
        val pkg = intent.getParcelableExtraCompat(EXTRA_PACKAGE_INFO, UploadPackage::class.java)
            ?: return false
        val apk = intent.getParcelableExtraCompat(EXTRA_APK_INFO, UploadApk::class.java)
        val info = intent.getParcelableExtraCompat(EXTRA_INFO, UploadInfo::class.java)
            ?: return false

        println("[upload service] onStartCommand(pkg = $pkg, apk = $apk, info = $info)")

        val id = pkg.uniqueId

        trackTransfer(id)

        // Now that extras parsed, swap the placeholder title for the real app label
        val label = apk?.packageInfo?.applicationInfo?.loadLabel(packageManager)?.toString()
            ?: pkg.uniqueId
        startForegroundCompat(notifications.createInitialNotification(label))

        // Start the upload first so the relay's cached state is fresh (AWAIT)
        // before any subscriber attaches. Otherwise BehaviorRelay would replay
        // the previous terminal state (e.g. ERROR after a retry), and the
        // notification subscriber would immediately stop the foreground service.
        uploadManager.upload(id, pkg, apk, info)

        val relay = uploadManager.status(id)

        if (apk != null) {
            notifications.subscribe(
                id = id,
                pkg = pkg,
                apk = apk,
                info = info,
                stop = {
                    handler.post { onTransferFinished(id) }
                },
                observable = relay,
            )
        } else {
            // take(1) disposes itself, so a state already cached in the relay can't
            // leave a live subscription behind the way a captured Disposable would
            relay.filter { it.status in TERMINAL_UPLOAD_STATUSES }
                .take(1)
                .subscribe { handler.post { onTransferFinished(id) } }
        }

        return true
    }

}

fun createUploadIntent(
    context: Context,
    pkg: UploadPackage,
    apk: UploadApk?,
    info: UploadInfo,
): Intent = Intent(context, UploadService::class.java)
    .putExtra(EXTRA_PACKAGE_INFO, pkg)
    .putExtra(EXTRA_APK_INFO, apk)
    .putExtra(EXTRA_INFO, info)

private const val EXTRA_PACKAGE_INFO = "pkg"
private const val EXTRA_APK_INFO = "apk"
private const val EXTRA_INFO = "info"
