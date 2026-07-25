package com.tomclaw.appsend.screen.details.adapter.similar_app

import android.os.Parcelable
import com.tomclaw.appsend.util.adapter.Item
import kotlinx.parcelize.Parcelize

/** One app tile in the "Similar apps" rail. */
@Parcelize
data class SimilarAppItem(
    override val id: Long,
    val appId: String,
    val title: String,
    val icon: String?,
    val rating: Float,
) : Item, Parcelable
