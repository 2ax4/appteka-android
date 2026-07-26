package com.tomclaw.appsend.screen.search

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.annotation.LayoutRes
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
import com.tomclaw.appsend.util.applyBottomInsets
import com.tomclaw.appsend.util.changes
import com.tomclaw.appsend.util.clicks
import com.tomclaw.appsend.util.hideWithAlphaAnimation
import com.tomclaw.appsend.util.showWithAlphaAnimation
import io.reactivex.rxjava3.core.Observable

interface SearchView {

    fun showProgress()

    fun showContent()

    fun contentUpdated()

    /** A fresh result set starts at the top, not where the last one was left. */
    fun scrollToTop()

    /** Nothing searched for yet — the state that offers popular tags. */
    fun showPlaceholder()

    /** Searched, and nothing matched. */
    fun showEmptyResult()

    fun showError()

    fun stopPullRefreshing()

    fun isPullRefreshing(): Boolean

    fun setQueryText(query: String)

    fun requestQueryFocus()

    /**
     * The filter row next to the query: [selected] is what search is
     * narrowed by, [suggestions] is what it could be narrowed by next,
     * and [custom] is the typed text offered as a tag of its own.
     */
    fun showTags(selected: List<String>, suggestions: List<String>, custom: String?)

    fun showPopularTags(tags: List<String>)

    fun retryClicks(): Observable<Unit>

    fun refreshClicks(): Observable<Unit>

    fun queryTextChanges(): Observable<String>

    fun tagRemoveClicks(): Observable<String>

    fun tagSuggestionClicks(): Observable<String>

    fun customTagClicks(): Observable<String>

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
    private val tagsScroll: View = rootView.findViewById(R.id.tags_scroll)
    private val tagsGroup: ChipGroup = rootView.findViewById(R.id.tags)
    private val popularTagsTitle: View = rootView.findViewById(R.id.popular_tags_title)
    private val popularTags: ChipGroup = rootView.findViewById(R.id.popular_tags)

    private val retryRelay = PublishRelay.create<Unit>()
    private val refreshRelay = PublishRelay.create<Unit>()
    private val queryTextRelay = PublishRelay.create<String>()
    private val tagRemoveRelay = PublishRelay.create<String>()
    private val tagSuggestionRelay = PublishRelay.create<String>()
    private val customTagRelay = PublishRelay.create<String>()
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

        // Insets: the list keeps its last row clear of the
        // navigation bar while still scrolling underneath it.
        recycler.applyBottomInsets()
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

    override fun scrollToTop() {
        recycler.scrollToPosition(0)
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

    override fun showTags(selected: List<String>, suggestions: List<String>, custom: String?) {
        tagsGroup.removeAllViews()
        for (tag in selected) {
            val chip = inflateChip(R.layout.search_selected_tag_chip, tagsGroup, tag)
            chip.setOnCloseIconClickListener { tagRemoveRelay.accept(tag) }
            tagsGroup.addView(chip)
        }
        for (tag in suggestions) {
            val chip = inflateChip(R.layout.search_tag_chip, tagsGroup, tag)
            chip.setOnClickListener { tagSuggestionRelay.accept(tag) }
            tagsGroup.addView(chip)
        }
        if (custom != null) {
            val label = context.getString(R.string.search_add_tag, custom)
            val chip = inflateChip(R.layout.search_custom_tag_chip, tagsGroup, label)
            chip.setOnClickListener { customTagRelay.accept(custom) }
            tagsGroup.addView(chip)
        }
        tagsScroll.isVisible = tagsGroup.childCount > 0
    }

    override fun showPopularTags(tags: List<String>) {
        popularTagsTitle.isVisible = tags.isNotEmpty()
        popularTags.removeAllViews()
        for (tag in tags) {
            val chip = inflateChip(R.layout.search_tag_chip, popularTags, tag)
            chip.setOnClickListener { popularTagRelay.accept(tag) }
            popularTags.addView(chip)
        }
    }

    private fun inflateChip(@LayoutRes layout: Int, group: ChipGroup, text: String): Chip {
        val chip = LayoutInflater.from(context).inflate(layout, group, false) as Chip
        chip.text = text
        // Without this the invisible 48dp touch target pads every
        // chip and the rows drift apart (see TagsItemView).
        chip.setEnsureMinTouchTargetSize(false)
        return chip
    }

    override fun retryClicks(): Observable<Unit> = retryRelay

    override fun refreshClicks(): Observable<Unit> = refreshRelay

    override fun queryTextChanges(): Observable<String> = queryTextRelay

    override fun tagRemoveClicks(): Observable<String> = tagRemoveRelay

    override fun tagSuggestionClicks(): Observable<String> = tagSuggestionRelay

    override fun customTagClicks(): Observable<String> = customTagRelay

    override fun popularTagClicks(): Observable<String> = popularTagRelay

}

private const val DURATION_MEDIUM = 300L

// ViewFlipper child order, mirroring activity_search.xml.
private const val CHILD_CONTENT = 0
private const val CHILD_PLACEHOLDER = 1
private const val CHILD_EMPTY = 2
private const val CHILD_ERROR = 3
