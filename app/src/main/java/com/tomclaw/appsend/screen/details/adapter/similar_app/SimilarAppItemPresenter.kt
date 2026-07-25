package com.tomclaw.appsend.screen.details.adapter.similar_app

import com.tomclaw.appsend.util.adapter.ItemPresenter
import com.tomclaw.appsend.screen.details.adapter.similar.SimilarAppItemListener

class SimilarAppItemPresenter(
    private val listener: SimilarAppItemListener,
) : ItemPresenter<SimilarAppItemView, SimilarAppItem> {

    override fun bindView(view: SimilarAppItemView, item: SimilarAppItem, position: Int) {
        with(view) {
            setIcon(item.icon)
            setTitle(item.title)
            setRating(item.rating)
            setOnClickListener { listener.onSimilarAppClick(item.appId, item.title) }
        }
    }

}
