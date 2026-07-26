package com.tomclaw.appsend.screen.details.adapter.all_ratings

import com.tomclaw.appsend.screen.details.adapter.ItemListener
import com.tomclaw.appsend.util.adapter.ItemPresenter

class AllRatingsItemPresenter(
    private val listener: ItemListener,
) : ItemPresenter<AllRatingsItemView, AllRatingsItem> {

    override fun bindView(view: AllRatingsItemView, item: AllRatingsItem, position: Int) {
        view.setCount(item.rateCount)
        view.setOnClickListener { listener.onScoresClick() }
    }

}
