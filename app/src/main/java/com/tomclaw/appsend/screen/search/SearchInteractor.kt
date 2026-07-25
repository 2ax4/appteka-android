package com.tomclaw.appsend.screen.search

import com.tomclaw.appsend.core.StoreApi
import com.tomclaw.appsend.dto.AppEntity
import com.tomclaw.appsend.util.SchedulersFactory
import io.reactivex.rxjava3.core.Observable
import java.util.Locale

interface SearchInteractor {

    /**
     * Text and tags are independent filters. Either narrows the catalog
     * on its own; together, tags narrow what the text found.
     */
    fun searchApps(
        query: String,
        tags: List<String>,
        offset: Int = 0
    ): Observable<List<AppEntity>>

    /** Tags carried by enough apps to be worth offering as a starting point. */
    fun loadPopularTags(): Observable<List<String>>

}

class SearchInteractorImpl(
    private val api: StoreApi,
    private val locale: Locale,
    private val schedulers: SchedulersFactory
) : SearchInteractor {

    override fun searchApps(
        query: String,
        tags: List<String>,
        offset: Int
    ): Observable<List<AppEntity>> {
        return api.searchApps(
            query = query.takeIf { it.isNotBlank() },
            tags = tags.takeIf { it.isNotEmpty() }?.joinToString(separator = ","),
            offset = offset.takeIf { it > 0 },
            locale = locale.language
        )
            .map { list ->
                list.result.files
            }
            .toObservable()
            .subscribeOn(schedulers.io())
    }

    override fun loadPopularTags(): Observable<List<String>> {
        return api.getTags(minCount = POPULAR_TAGS_MIN_COUNT)
            .map { response ->
                response.result.tags
                    .take(POPULAR_TAGS_COUNT)
                    .map { it.tag }
            }
            .toObservable()
            .subscribeOn(schedulers.io())
    }

}

// The vocabulary has a long tail — most tags belong to a couple of apps
// — so the suggestions come from the substantial end of it.
private const val POPULAR_TAGS_MIN_COUNT = 3
private const val POPULAR_TAGS_COUNT = 30
