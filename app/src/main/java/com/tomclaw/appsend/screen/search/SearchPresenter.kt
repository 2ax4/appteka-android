package com.tomclaw.appsend.screen.search

import android.os.Bundle
import com.tomclaw.appsend.util.adapter.AdapterPresenter
import com.tomclaw.appsend.util.adapter.Item
import com.tomclaw.appsend.dto.AppEntity
import com.tomclaw.appsend.screen.store.AppConverter
import com.tomclaw.appsend.screen.store.adapter.app.AppItem
import com.tomclaw.appsend.screen.store.adapter.ItemListener
import com.tomclaw.appsend.util.Analytics
import com.tomclaw.appsend.util.SchedulersFactory
import com.tomclaw.appsend.util.getParcelableArrayListCompat
import com.tomclaw.appsend.util.retryWhenNonAuthErrors
import dagger.Lazy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit

interface SearchPresenter : ItemListener {

    fun attachView(view: SearchView)

    fun detachView()

    fun attachRouter(router: SearchRouter)

    fun detachRouter()

    fun saveState(): Bundle

    fun invalidateSearch()

    interface SearchRouter {

        fun openAppScreen(appId: String, title: String)

    }

}

/**
 * Search over the catalog by free text, by tags, or by both at once.
 *
 * There is deliberately no notion of a "tag screen" here: arriving from
 * a tag on an app page just seeds [tags] with it. Text and tags are two
 * fields of the same query, so every path through this class is the
 * same path — the criteria change, the search runs.
 */
class SearchPresenterImpl(
    private val searchInteractor: SearchInteractor,
    private val adapterPresenter: Lazy<AdapterPresenter>,
    private val appConverter: AppConverter,
    private val analytics: Analytics,
    private val schedulers: SchedulersFactory,
    initialTags: List<String>,
    state: Bundle?
) : SearchPresenter {

    private var view: SearchView? = null
    private var router: SearchPresenter.SearchRouter? = null

    private val subscriptions = CompositeDisposable()

    private var items: List<AppItem>? =
        state?.getParcelableArrayListCompat(KEY_APPS, AppItem::class.java)
    private var isError: Boolean = state?.getBoolean(KEY_ERROR) == true

    private var query: String = state?.getString(KEY_QUERY).orEmpty()
    private var tags: List<String> =
        state?.getStringArrayList(KEY_TAGS) ?: initialTags

    private var popularTags: List<String>? =
        state?.getStringArrayList(KEY_POPULAR_TAGS)

    private val hasCriteria: Boolean
        get() = query.isNotBlank() || tags.isNotEmpty()

    override fun attachView(view: SearchView) {
        this.view = view

        subscriptions += view.retryClicks().subscribe {
            performSearch()
        }
        subscriptions += view.refreshClicks().subscribe {
            invalidateSearch()
            analytics.trackEvent("search-refresh")
        }
        subscriptions += view.queryTextChanges()
            .debounce(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS, schedulers.mainThread())
            .distinctUntilChanged()
            .subscribe { text ->
                query = text
                onCriteriaChanged()
            }
        subscriptions += view.tagRemoveClicks().subscribe { tag ->
            tags = tags.filterNot { it == tag }
            onCriteriaChanged()
        }
        subscriptions += view.popularTagClicks().subscribe { tag ->
            if (tags.contains(tag)) return@subscribe
            tags = tags + tag
            analytics.trackEvent("search-popular-tag")
            onCriteriaChanged()
        }

        if (query.isNotEmpty()) {
            view.setQueryText(query)
        }
        view.showSelectedTags(tags)

        when {
            isError -> onError()
            items != null -> bindItems()
            hasCriteria -> performSearch()
            else -> showPlaceholder()
        }
    }

    override fun detachView() {
        subscriptions.clear()
        this.view = null
    }

    override fun attachRouter(router: SearchPresenter.SearchRouter) {
        this.router = router
    }

    override fun detachRouter() {
        this.router = null
    }

    override fun saveState() = Bundle().apply {
        putParcelableArrayList(KEY_APPS, items?.let { ArrayList(it) })
        putBoolean(KEY_ERROR, isError)
        putString(KEY_QUERY, query)
        putStringArrayList(KEY_TAGS, ArrayList(tags))
        popularTags?.let { putStringArrayList(KEY_POPULAR_TAGS, ArrayList(it)) }
    }

    override fun invalidateSearch() {
        items = null
        isError = false
        if (hasCriteria) performSearch() else showPlaceholder()
    }

    /** The one funnel: whatever changed the criteria, this decides what happens next. */
    private fun onCriteriaChanged() {
        view?.showSelectedTags(tags)
        if (hasCriteria) performSearch() else showPlaceholder()
    }

    private fun performSearch() {
        if (!hasCriteria) {
            showPlaceholder()
            return
        }

        items = null

        subscriptions += searchInteractor.searchApps(query.trim(), tags)
            .observeOn(schedulers.mainThread())
            .doOnSubscribe { if (view?.isPullRefreshing() == false) view?.showProgress() }
            .subscribe(
                { onLoaded(it, isNewSearch = true) },
                { onError() }
            )
    }

    private fun loadMore(offset: Int) {
        if (!hasCriteria) return

        subscriptions += searchInteractor.searchApps(query.trim(), tags, offset)
            .observeOn(schedulers.mainThread())
            .retryWhenNonAuthErrors()
            .subscribe(
                { onLoaded(it, isNewSearch = false) },
                { onError() }
            )
    }

    private fun onLoaded(entities: List<AppEntity>, isNewSearch: Boolean) {
        isError = false
        val newItems = entities
            .map { appConverter.convert(it) }
            .toList()
            .apply { if (isNotEmpty()) last().hasMore = true }

        this.items = if (isNewSearch) {
            newItems
        } else {
            this.items
                ?.apply { if (isNotEmpty()) last().hasProgress = false }
                ?.plus(newItems) ?: newItems
        }
        bindItems()
    }

    private fun bindItems() {
        val items = this.items
        if (items.isNullOrEmpty()) {
            // Nothing matched the criteria — that is a result, not an
            // invitation to browse tags, so no suggestions here.
            view?.showEmptyResult()
            return
        }
        adapterPresenter.get().onDataSourceChanged(items)
        view?.let {
            it.contentUpdated()
            if (it.isPullRefreshing()) {
                it.stopPullRefreshing()
            } else {
                it.showContent()
            }
        }
    }

    /** Nothing asked for yet — offer the catalog's own tags as a way in. */
    private fun showPlaceholder() {
        items = null
        view?.showPlaceholder()

        val loaded = popularTags
        if (loaded != null) {
            view?.showPopularTags(loaded.filterNot { tags.contains(it) })
            return
        }
        subscriptions += searchInteractor.loadPopularTags()
            .observeOn(schedulers.mainThread())
            .subscribe(
                { loadedTags ->
                    popularTags = loadedTags
                    view?.showPopularTags(loadedTags.filterNot { tags.contains(it) })
                },
                {
                    // Suggestions are a convenience; failing to load them
                    // leaves a plain placeholder rather than an error.
                    view?.showPopularTags(emptyList())
                }
            )
    }

    private fun onError() {
        this.isError = true
        view?.showError()
    }

    override fun onItemClick(item: Item) {
        val app = items?.find { it.id == item.id } ?: return
        router?.openAppScreen(app.appId, app.title)
    }

    override fun onLoadMore(item: Item) {
        val offset = items?.size ?: return
        loadMore(offset)
    }

}

private const val KEY_APPS = "apps"
private const val KEY_ERROR = "error"
private const val KEY_QUERY = "query"
private const val KEY_TAGS = "tags"
private const val KEY_POPULAR_TAGS = "popular_tags"
private const val DEBOUNCE_DELAY_MS = 500L
