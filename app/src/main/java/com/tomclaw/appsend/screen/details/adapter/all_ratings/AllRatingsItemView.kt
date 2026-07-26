package com.tomclaw.appsend.screen.details.adapter.all_ratings

import android.view.View
import com.google.android.material.button.MaterialButton
import com.tomclaw.appsend.R
import com.tomclaw.appsend.util.adapter.BaseItemViewHolder
import com.tomclaw.appsend.util.adapter.ItemView

interface AllRatingsItemView : ItemView {

    fun setCount(count: Int)

    fun setOnClickListener(listener: (() -> Unit)?)

}

class AllRatingsItemViewHolder(view: View) : BaseItemViewHolder(view), AllRatingsItemView {

    private val context = view.context
    private val button: MaterialButton = view.findViewById(R.id.all_ratings_button)

    private var clickListener: (() -> Unit)? = null

    init {
        button.setOnClickListener { clickListener?.invoke() }
    }

    override fun setCount(count: Int) {
        button.text = context.resources.getQuantityString(
            R.plurals.see_all_reviews,
            count,
            count,
        )
    }

    override fun setOnClickListener(listener: (() -> Unit)?) {
        this.clickListener = listener
    }

    override fun onUnbind() {
        this.clickListener = null
    }

}
