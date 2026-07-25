package com.tomclaw.appsend.screen.details.adapter.tags

import com.tomclaw.appsend.R
import com.tomclaw.appsend.util.adapter.Item
import com.tomclaw.appsend.util.adapter.ItemBlueprint
import com.tomclaw.appsend.util.adapter.ItemPresenter
import com.tomclaw.appsend.util.adapter.ViewHolderBuilder

class TagsItemBlueprint(
    override val presenter: ItemPresenter<TagsItemView, TagsItem>
) : ItemBlueprint<TagsItemView, TagsItem> {

    override val viewHolderProvider = ViewHolderBuilder.ViewHolderProvider(
        layoutId = R.layout.details_block_tags,
        creator = { _, view -> TagsItemViewHolder(view) }
    )

    override fun isRelevantItem(item: Item) = item is TagsItem

}
