package com.tomclaw.appsend.screen.details.adapter.similar

/**
 * Clicks inside the "Similar apps" rail. Separate from the screen's
 * [com.tomclaw.appsend.screen.details.adapter.ItemListener] for the
 * same reason screenshots have their own: the tiles live in a nested
 * adapter that only knows about its own rail.
 */
interface SimilarAppItemListener {

    fun onSimilarAppClick(appId: String, title: String)

}
