package com.tomclaw.appsend.screen.details.adapter.similar_app

import com.tomclaw.appsend.util.adapter.Item
import com.tomclaw.appsend.util.adapter.ItemBlueprint
import com.tomclaw.appsend.util.adapter.ItemPresenter
import com.tomclaw.appsend.util.adapter.ViewHolderBuilder
import com.tomclaw.appsend.R

class SimilarAppItemBlueprint(
    override val presenter: ItemPresenter<SimilarAppItemView, SimilarAppItem>,
) : ItemBlueprint<SimilarAppItemView, SimilarAppItem> {

    override val viewHolderProvider =
        ViewHolderBuilder.ViewHolderProvider(
            layoutId = R.layout.details_similar_app_item,
            creator = { _, view -> SimilarAppItemViewHolder(view) }
        )

    override fun isRelevantItem(item: Item) = item is SimilarAppItem

}
