package com.tomclaw.appsend.screen.feed

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.tomclaw.appsend.util.adapter.SimpleRecyclerAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxrelay3.PublishRelay
import com.tomclaw.appsend.R
import com.tomclaw.appsend.core.permissions.Capability
import com.tomclaw.appsend.core.permissions.CapabilityHintResolver
import com.tomclaw.appsend.util.applyBottomInsets
import com.tomclaw.appsend.util.ActionItem
import com.tomclaw.appsend.util.ActionsAdapter
import com.tomclaw.appsend.util.clicks
import com.tomclaw.appsend.util.hide
import com.tomclaw.appsend.util.hideWithAlphaAnimation
import com.tomclaw.appsend.util.show
import com.tomclaw.appsend.util.showWithAlphaAnimation
import io.reactivex.rxjava3.core.Observable

interface FeedView {

    fun showProgress()

    fun showContent()

    fun showToolbar()

    fun hideToolbar()

    fun contentUpdated()

    fun contentUpdated(position: Int)

    fun rangeInserted(position: Int, count: Int)

    /**
     * Insert [count] items on top of the list and re-bind the boundary
     * item now sitting at [position], keeping every item already on
     * screen exactly where the user sees it.
     */
    fun rangePrepended(count: Int, position: Int)

    fun rangeDeleted(position: Int, count: Int)

    fun scrollTo(position: Int)

    fun scrollToBottom()

    fun showPlaceholder()

    fun showError()

    fun showPostDeletionFailed()

    fun showUnauthorizedError()

    /**
     * Surface a capability-denied response (server rejected the
     * action because of an ACL/ownership/role check). Routed through
     * the same hint resolver as proactive UI.
     */
    fun showCapabilityDenied(capability: Capability)

    fun showPostMenu(actions: List<MenuAction>)

    fun navigationClicks(): Observable<Unit>

    fun retryClicks(): Observable<Unit>

    fun scrollIdle(): Observable<Int>

}

class FeedViewImpl(
    private val view: View,
    private val adapter: SimpleRecyclerAdapter,
    private val preferences: FeedPreferencesProvider,
) : FeedView {

    private val context = view.context
    private val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    private val appBar: View = view.findViewById(R.id.toolbar_container)
    private val flipper: ViewFlipper = view.findViewById(R.id.view_flipper)
    private val overlayProgress: View = view.findViewById(R.id.overlay_progress)
    private val recycler: RecyclerView = view.findViewById(R.id.recycler)
    private val error: TextView = view.findViewById(R.id.error_text)
    private val retryButton: View = view.findViewById(R.id.button_retry)

    private val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

    private val navigationRelay = PublishRelay.create<Unit>()
    private val retryRelay = PublishRelay.create<Unit>()
    private val scrollIdleRelay = PublishRelay.create<Int>()

    init {
        toolbar.setNavigationOnClickListener { navigationRelay.accept(Unit) }
        toolbar.setTitle(R.string.user_feed)

        adapter.setHasStableIds(true)
        recycler.adapter = adapter
        recycler.layoutManager = layoutManager
        recycler.itemAnimator = DefaultItemAnimator()
        recycler.itemAnimator?.changeDuration = DURATION_MEDIUM
        recycler.addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scrollIdleRelay.accept(layoutManager.findLastVisibleItemPosition())
                }
            }
        })

        retryButton.clicks(retryRelay)

        // Insets: content keeps clear of the navigation
        // bar while still scrolling underneath it.
        recycler.applyBottomInsets()
    }

    override fun showProgress() {
        flipper.displayedChild = 0
        overlayProgress.showWithAlphaAnimation(animateFully = true)
    }

    override fun showContent() {
        flipper.displayedChild = 0
        overlayProgress.hideWithAlphaAnimation(animateFully = false)
    }

    override fun showToolbar() {
        appBar.show()
    }

    /** See ProfileView.hideToolbar — the bar hides, not the toolbar. */
    override fun hideToolbar() {
        appBar.hide()
    }

    override fun showPlaceholder() {
        flipper.displayedChild = 1
    }

    override fun showError() {
        flipper.displayedChild = 2

        error.setText(R.string.load_files_error)
    }

    override fun showPostDeletionFailed() {
        Snackbar.make(recycler, R.string.error_post_deletion, Snackbar.LENGTH_LONG).show()
    }

    override fun showUnauthorizedError() {
        Snackbar.make(recycler, R.string.authorization_required_message, Snackbar.LENGTH_LONG).show()
    }

    override fun showCapabilityDenied(capability: Capability) {
        val text = CapabilityHintResolver(recycler.resources).resolveText(capability)
        Snackbar.make(recycler, text, Snackbar.LENGTH_LONG).show()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun contentUpdated() {
        adapter.notifyDataSetChanged()
    }

    override fun contentUpdated(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun rangeInserted(position: Int, count: Int) {
        adapter.notifyItemRangeInserted(position, count)
    }

    /**
     * The boundary item shrinks as its spinner goes away, and by default
     * the layout manager keeps that item's top edge, which drags the whole
     * screen up and pushes the fresh posts out of the viewport. Pin the
     * item right below the boundary instead: its size doesn't change, so
     * nothing visible moves and the space the spinner leaves behind is
     * taken by the tail of the prepended posts.
     */
    override fun rangePrepended(count: Int, position: Int) {
        if (count == 0) {
            adapter.notifyItemChanged(position)
            return
        }
        val anchor = position + 1
        // Offsets of a pending scroll are counted from the padding, unlike
        // the decorated bounds of an already laid out child.
        val anchorOffset = layoutManager.findViewByPosition(0)
            ?.let { layoutManager.getDecoratedBottom(it) - layoutManager.paddingTop }
            ?.takeIf { anchor < adapter.itemCount }

        // Everything on screen stays put, so item animations would have
        // nothing to show and would only fight the pinned position.
        withoutItemAnimations {
            adapter.notifyItemRangeInserted(0, count)
            adapter.notifyItemChanged(position)
            anchorOffset?.let { layoutManager.scrollToPositionWithOffset(anchor, it) }
        }
    }

    /** Runs [block] unanimated, restoring the animator after its layout pass. */
    private fun withoutItemAnimations(block: () -> Unit) {
        val animator = recycler.itemAnimator
        if (animator == null) {
            block()
            return
        }
        recycler.itemAnimator = null
        block()
        recycler.viewTreeObserver.addOnPreDrawListener(object : OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                recycler.viewTreeObserver.removeOnPreDrawListener(this)
                recycler.itemAnimator = animator
                return true
            }
        })
    }

    override fun rangeDeleted(position: Int, count: Int) {
        adapter.notifyItemRangeRemoved(position, count)
    }

    override fun scrollTo(position: Int) {
        recycler.scrollToPosition(position)
    }

    override fun scrollToBottom() {
        val itemCount = adapter.itemCount
        if (itemCount > 0) {
            recycler.scrollToPosition(itemCount - 1)
        }
    }

    override fun showPostMenu(actions: List<MenuAction>) {
        val bottomSheetDialog = BottomSheetDialog(context)
        val sheetView = View.inflate(context, R.layout.bottom_sheet_actions, null)
        val actionsRecycler: RecyclerView = sheetView.findViewById(R.id.actions_recycler)

        val actionItems = actions.map { action ->
            ActionItem(action.id, action.title, action.icon)
        }

        val actionsAdapter = ActionsAdapter(actionItems) { actionId ->
            bottomSheetDialog.dismiss()
            actions.firstOrNull { it.id == actionId }?.action?.invoke()
        }

        actionsRecycler.layoutManager = LinearLayoutManager(context)
        actionsRecycler.adapter = actionsAdapter

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    override fun navigationClicks(): Observable<Unit> = navigationRelay

    override fun retryClicks(): Observable<Unit> = retryRelay

    override fun scrollIdle(): Observable<Int> = scrollIdleRelay

}

data class MenuAction(
    val id: Int,
    val title: String,
    @DrawableRes val icon: Int,
    val action: () -> Unit,
)

private const val DURATION_MEDIUM = 300L
