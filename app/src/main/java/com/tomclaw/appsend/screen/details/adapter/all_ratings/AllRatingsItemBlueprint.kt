package com.tomclaw.appsend.screen.details.adapter.all_ratings

import com.tomclaw.appsend.R
import com.tomclaw.appsend.util.adapter.Item
import com.tomclaw.appsend.util.adapter.ItemBlueprint
import com.tomclaw.appsend.util.adapter.ItemPresenter
import com.tomclaw.appsend.util.adapter.ViewHolderBuilder

class AllRatingsItemBlueprint(
    override val presenter: ItemPresenter<AllRatingsItemView, AllRatingsItem>
) : ItemBlueprint<AllRatingsItemView, AllRatingsItem> {

    override val viewHolderProvider = ViewHolderBuilder.ViewHolderProvider(
        layoutId = R.layout.details_block_all_ratings,
        creator = { _, view -> AllRatingsItemViewHolder(view) }
    )

    override fun isRelevantItem(item: Item) = item is AllRatingsItem

}
