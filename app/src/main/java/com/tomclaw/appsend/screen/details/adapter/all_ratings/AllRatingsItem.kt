package com.tomclaw.appsend.screen.details.adapter.all_ratings

import android.os.Parcelable
import com.tomclaw.appsend.util.adapter.Item
import kotlinx.parcelize.Parcelize

@Parcelize
data class AllRatingsItem(
    override val id: Long,
    val rateCount: Int,
) : Item, Parcelable
