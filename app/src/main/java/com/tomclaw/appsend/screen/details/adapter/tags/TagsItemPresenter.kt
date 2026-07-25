package com.tomclaw.appsend.screen.details.adapter.tags

import com.tomclaw.appsend.screen.details.adapter.ItemListener
import com.tomclaw.appsend.util.adapter.ItemPresenter

class TagsItemPresenter(
    private val listener: ItemListener,
) : ItemPresenter<TagsItemView, TagsItem> {

    override fun bindView(view: TagsItemView, item: TagsItem, position: Int) {
        view.showTags(item.tags)
        view.setOnTagClickListener { tag -> listener.onTagClick(tag) }
    }

}
