package com.tomclaw.appsend.screen.details.adapter.similar

import com.tomclaw.appsend.util.adapter.Item
import com.tomclaw.appsend.screen.profile.adapter.app.AppItem

/**
 * Reuses the profile's app tile rather than defining another one: this
 * rail and the profile's "Uploaded" shelf are the same thing in a
 * different place, and they should stay identical without anyone having
 * to remember to change both.
 */
data class SimilarItem(
    override val id: Long,
    val items: List<AppItem>,
) : Item
