package com.tomclaw.appsend.screen.feed.adapter

import android.os.Parcelable
import com.tomclaw.appsend.util.adapter.Item
import com.tomclaw.appsend.screen.feed.api.Reaction
import com.tomclaw.appsend.user.api.UserBrief

interface FeedItem : Item, Parcelable {
    val user: UserBrief?
    val actions: List<String>?
    var hasMore: Boolean

    /** Side of the item the pagination spinner is drawn at, null when idle. */
    var progress: ProgressSide?

    fun getReactions(): List<Reaction>?

    fun withReactions(reactions: List<Reaction>): FeedItem
}

/** The feed paginates both ways, so the spinner belongs to a certain end of the list. */
enum class ProgressSide {
    Top,
    Bottom,
}
