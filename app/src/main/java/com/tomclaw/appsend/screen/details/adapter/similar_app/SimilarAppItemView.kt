package com.tomclaw.appsend.screen.details.adapter.similar_app

import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.tomclaw.appsend.util.adapter.BaseItemViewHolder
import com.tomclaw.appsend.util.adapter.ItemView
import com.tomclaw.appsend.R
import com.tomclaw.imageloader.util.fetch

interface SimilarAppItemView : ItemView {

    fun setIcon(url: String?)

    fun setTitle(title: String)

    fun setRating(rating: Float)

    fun setOnClickListener(listener: (() -> Unit)?)

}

class SimilarAppItemViewHolder(view: View) : BaseItemViewHolder(view), SimilarAppItemView {

    private val icon: ImageView = view.findViewById(R.id.app_icon)
    private val title: TextView = view.findViewById(R.id.app_title)
    private val rating: RatingBar = view.findViewById(R.id.app_rating)

    private var clickListener: (() -> Unit)? = null

    init {
        view.setOnClickListener { clickListener?.invoke() }
    }

    override fun setIcon(url: String?) {
        icon.fetch(url.orEmpty()) {
            centerCrop()
            placeholder(R.drawable.app_placeholder)
            onLoading { imageView ->
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageResource(R.drawable.app_placeholder)
            }
        }
    }

    override fun setTitle(title: String) {
        this.title.text = title
    }

    override fun setRating(rating: Float) {
        // An unrated app shows no stars at all — an empty five-star row
        // reads as "rated badly", which isn't what zero means here.
        this.rating.isVisible = rating > 0
        this.rating.rating = rating
    }

    override fun setOnClickListener(listener: (() -> Unit)?) {
        this.clickListener = listener
    }

    override fun onUnbind() {
        this.clickListener = null
    }

}
