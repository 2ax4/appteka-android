package com.tomclaw.appsend.screen.details.adapter.similar

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tomclaw.appsend.util.adapter.BaseItemViewHolder
import com.tomclaw.appsend.util.adapter.SimpleRecyclerAdapter
import com.tomclaw.appsend.util.adapter.ItemView
import com.tomclaw.appsend.R

interface SimilarItemView : ItemView {

    fun notifyChanged()

}

class SimilarItemViewHolder(
    view: View,
    private val adapter: SimpleRecyclerAdapter,
) : BaseItemViewHolder(view), SimilarItemView {

    private val recycler: RecyclerView = view.findViewById(R.id.recycler)

    init {
        val layoutManager = LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false)
        adapter.setHasStableIds(true)
        recycler.adapter = adapter
        recycler.layoutManager = layoutManager
        recycler.itemAnimator = DefaultItemAnimator()
        recycler.itemAnimator?.changeDuration = DURATION_MEDIUM
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun notifyChanged() {
        adapter.notifyDataSetChanged()
    }

    override fun onUnbind() {
    }

}

private const val DURATION_MEDIUM = 300L
