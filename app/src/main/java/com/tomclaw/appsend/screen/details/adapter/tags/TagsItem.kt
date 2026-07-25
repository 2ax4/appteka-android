package com.tomclaw.appsend.screen.details.adapter.tags

import android.os.Parcelable
import com.tomclaw.appsend.util.adapter.Item
import kotlinx.parcelize.Parcelize

@Parcelize
data class TagsItem(
    override val id: Long,
    val tags: List<String>,
) : Item, Parcelable
