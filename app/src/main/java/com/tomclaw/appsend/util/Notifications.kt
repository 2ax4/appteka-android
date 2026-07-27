package com.tomclaw.appsend.util

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import com.tomclaw.appsend.BuildConfig
import com.tomclaw.appsend.screen.details.createDetailsActivityIntent

/** Groups every notification the app posts, whatever it is transferring. */
const val GROUP_NOTIFICATIONS = BuildConfig.APPLICATION_ID + ".NOTIFICATIONS"

/**
 * Opens an app page from a notification. Extras play no part in PendingIntent
 * identity, so [requestCode] is what keeps one notification's intent from
 * standing in for another's — pass something derived from the notification id.
 */
fun Context.openDetailsPendingIntent(
    requestCode: Int,
    appId: String,
    label: String,
): PendingIntent = PendingIntent.getActivity(
    this,
    requestCode,
    createDetailsActivityIntent(
        context = this,
        appId = appId,
        packageName = null,
        label = label,
        moderation = false,
        finishOnly = false,
    ).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
    FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE,
)
