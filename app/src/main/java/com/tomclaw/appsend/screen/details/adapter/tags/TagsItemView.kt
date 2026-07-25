package com.tomclaw.appsend.screen.details.adapter.tags

import android.view.LayoutInflater
import android.view.View
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tomclaw.appsend.R
import com.tomclaw.appsend.util.adapter.BaseItemViewHolder
import com.tomclaw.appsend.util.adapter.ItemView

interface TagsItemView : ItemView {

    fun showTags(tags: List<String>)

    fun setOnTagClickListener(listener: ((String) -> Unit)?)

}

class TagsItemViewHolder(view: View) : BaseItemViewHolder(view), TagsItemView {

    private val chips: ChipGroup = view.findViewById(R.id.tags_chips)

    private var tagClickListener: ((String) -> Unit)? = null

    override fun showTags(tags: List<String>) {
        chips.removeAllViews()
        val inflater = LayoutInflater.from(itemView.context)
        for (tag in tags) {
            val chip = inflater.inflate(R.layout.details_tag_chip, chips, false) as Chip
            chip.text = tag
            chip.setOnClickListener { tagClickListener?.invoke(tag) }
            chips.addView(chip)
        }
    }

    override fun setOnTagClickListener(listener: ((String) -> Unit)?) {
        this.tagClickListener = listener
    }

    override fun onUnbind() {
        this.tagClickListener = null
    }

}
