package com.tomclaw.appsend.screen.search

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tomclaw.appsend.util.adapter.SimpleRecyclerAdapter
import com.jakewharton.rxrelay3.PublishRelay
import com.tomclaw.appsend.R
import com.tomclaw.appsend.util.changes
import com.tomclaw.appsend.util.clicks
import com.tomclaw.appsend.util.hideWithAlphaAnimation
import com.tomclaw.appsend.util.showWithAlphaAnimation
import io.reactivex.rxjava3.core.Observable

interface SearchView {

    fun showProgress()

    fun showContent()

    fun contentUpdated()

    /** Nothing searched for yet — the state that offers popular tags. */
    fun showPlaceholder()

    /** Searched, and nothing matched. */
    fun showEmptyResult()

    fun showError()

    fun stopPullRefreshing()

    fun isPullRefreshing(): Boolean

    fun setQueryText(query: String)

    fun requestQueryFocus()

    fun showSelectedTags(tags: List<String>)

    fun showPopularTags(tags: List<String>)

    fun retryClicks(): Observable<Unit>

    fun refreshClicks(): Observable<Unit>

    fun queryTextChanges(): Observable<String>

    fun tagRemoveClicks(): Observable<String>

    fun popularTagClicks(): Observable<String>

}

class SearchViewImpl(
    rootView: View,
    private val adapter: SimpleRecyclerAdapter,
) : SearchView {

    private val context = rootView.context
    private val refresher: SwipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh)
    private val flipper: ViewFlipper = rootView.findViewById(R.id.view_flipper)
    private val overlayProgress: View = rootView.findViewById(R.id.overlay_progress)
    private val recycler: RecyclerView = rootView.findViewById(R.id.recycler)
    private val error: TextView = rootView.findViewById(R.id.error_text)
    private val retryButton: View = rootView.findViewById(R.id.button_retry)
    private val queryEdit: EditText = rootView.findViewById(R.id.query_edit)
    private val selectedTagsScroll: View = rootView.findViewById(R.id.selected_tags_scroll)
    private val selectedTags: ChipGroup = rootView.findViewById(R.id.selected_tags)
    private val popularTagsTitle: View = rootView.findViewById(R.id.popular_tags_title)
    private val popularTags: ChipGroup = rootView.findViewById(R.id.popular_tags)

    private val retryRelay = PublishRelay.create<Unit>()
    private val refreshRelay = PublishRelay.create<Unit>()
    private val queryTextRelay = PublishRelay.create<String>()
    private val tagRemoveRelay = PublishRelay.create<String>()
    private val popularTagRelay = PublishRelay.create<String>()

    init {
        val orientation = RecyclerView.VERTICAL
        val layoutManager = LinearLayoutManager(context, orientation, false)
        adapter.setHasStableIds(true)
        recycler.adapter = adapter
        recycler.layoutManager = layoutManager
        recycler.itemAnimator = DefaultItemAnimator()
        recycler.itemAnimator?.changeDuration = DURATION_MEDIUM

        refresher.setOnRefreshListener { refreshRelay.accept(Unit) }

        queryEdit.changes { text ->
            queryTextRelay.accept(text)
        }
    }

    override fun showProgress() {
        refresher.isEnabled = false
        flipper.displayedChild = CHILD_CONTENT
        overlayProgress.showWithAlphaAnimation(animateFully = true)
    }

    override fun showContent() {
        refresher.isEnabled = true
        flipper.displayedChild = CHILD_CONTENT
        overlayProgress.hideWithAlphaAnimation(animateFully = false)
    }

    override fun showPlaceholder() {
        refresher.isRefreshing = false
        refresher.isEnabled = false
        flipper.displayedChild = CHILD_PLACEHOLDER
        overlayProgress.hideWithAlphaAnimation(animateFully = false)
    }

    override fun showEmptyResult() {
        refresher.isRefreshing = false
        refresher.isEnabled = true
        flipper.displayedChild = CHILD_EMPTY
        overlayProgress.hideWithAlphaAnimation(animateFully = false)
    }

    override fun showError() {
        refresher.isEnabled = true
        flipper.displayedChild = CHILD_ERROR

        error.setText(R.string.load_files_error)
        retryButton.clicks(retryRelay)
    }

    override fun contentUpdated() {
        adapter.notifyDataSetChanged()
    }

    override fun stopPullRefreshing() {
        refresher.isRefreshing = false
    }

    override fun isPullRefreshing(): Boolean = refresher.isRefreshing

    override fun setQueryText(query: String) {
        queryEdit.setText(query)
        queryEdit.setSelection(query.length)
    }

    override fun requestQueryFocus() {
        queryEdit.requestFocus()
    }

    override fun showSelectedTags(tags: List<String>) {
        selectedTagsScroll.isVisible = tags.isNotEmpty()
        bindChips(selectedTags, tags, closeable = true) { tagRemoveRelay.accept(it) }
    }

    override fun showPopularTags(tags: List<String>) {
        popularTagsTitle.isVisible = tags.isNotEmpty()
        bindChips(popularTags, tags, closeable = false) { popularTagRelay.accept(it) }
    }

    /**
     * Fills a group with one chip per tag. Selected tags carry a close
     * icon and report removals; suggestions report plain clicks — the
     * only difference between the two rows.
     */
    private fun bindChips(
        group: ChipGroup,
        tags: List<String>,
        closeable: Boolean,
        onAction: (String) -> Unit,
    ) {
        group.removeAllViews()
        val inflater = LayoutInflater.from(group.context)
        for (tag in tags) {
            val chip = inflater.inflate(R.layout.search_tag_chip, group, false) as Chip
            chip.text = tag
            chip.isCloseIconVisible = closeable
            // Without this the invisible 48dp touch target pads every
            // chip and the rows drift apart (see TagsItemView).
            chip.setEnsureMinTouchTargetSize(false)
            if (closeable) {
                chip.setOnCloseIconClickListener { onAction(tag) }
            } else {
                chip.setOnClickListener { onAction(tag) }
            }
            group.addView(chip)
        }
    }

    override fun retryClicks(): Observable<Unit> = retryRelay

    override fun refreshClicks(): Observable<Unit> = refreshRelay

    override fun queryTextChanges(): Observable<String> = queryTextRelay

    override fun tagRemoveClicks(): Observable<String> = tagRemoveRelay

    override fun popularTagClicks(): Observable<String> = popularTagRelay

}

private const val DURATION_MEDIUM = 300L

// ViewFlipper child order, mirroring activity_search.xml.
private const val CHILD_CONTENT = 0
private const val CHILD_PLACEHOLDER = 1
private const val CHILD_EMPTY = 2
private const val CHILD_ERROR = 3
