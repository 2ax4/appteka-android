package com.tomclaw.appsend.screen.search

import com.tomclaw.appsend.util.GsonModel

/**
 * One search worth offering again. Text and tags are two fields of the
 * same query on this screen, so they are remembered as one thing, and
 * two searches are the same search when both fields agree.
 */
@GsonModel
data class SearchHistoryEntry(
    val query: String,
    val tags: List<String>,
)
