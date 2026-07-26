package com.tomclaw.appsend.screen.feed.adapter

import android.view.View
import com.tomclaw.appsend.R
import com.tomclaw.appsend.util.hide
import com.tomclaw.appsend.util.show

/** Pagination spinners drawn above and below the post content. */
class ItemPaginator(view: View) {

    private val top: View = view.findViewById(R.id.progress_top)
    private val bottom: View = view.findViewById(R.id.progress_bottom)

    fun bind(side: ProgressSide?) {
        if (side == ProgressSide.Top) top.show() else top.hide()
        if (side == ProgressSide.Bottom) bottom.show() else bottom.hide()
    }

}
