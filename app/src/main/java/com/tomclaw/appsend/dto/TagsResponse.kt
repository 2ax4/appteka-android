package com.tomclaw.appsend.dto

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.tomclaw.appsend.util.GsonModel
import kotlinx.parcelize.Parcelize

@GsonModel
@Parcelize
data class TagsResponse(
    @SerializedName("tags")
    val tags: List<TagEntity>
) : Parcelable

/** One tag of the catalog vocabulary and how many apps carry it. */
@GsonModel
@Parcelize
data class TagEntity(
    @SerializedName("tag")
    val tag: String,
    @SerializedName("count")
    val count: Int
) : Parcelable
