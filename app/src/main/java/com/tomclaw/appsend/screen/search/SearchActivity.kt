package com.tomclaw.appsend.screen.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tomclaw.appsend.util.adapter.ItemBinder
import com.tomclaw.appsend.util.adapter.AdapterPresenter
import com.tomclaw.appsend.util.adapter.SimpleRecyclerAdapter
import com.tomclaw.appsend.appComponent
import com.tomclaw.appsend.R
import com.tomclaw.appsend.screen.search.di.SearchModule
import com.tomclaw.appsend.util.ZipParcelable
import com.tomclaw.appsend.util.getParcelableCompat
import javax.inject.Inject

class SearchActivity : AppCompatActivity(), SearchPresenter.SearchRouter {

    @Inject
    lateinit var presenter: SearchPresenter

    @Inject
    lateinit var adapterPresenter: AdapterPresenter

    @Inject
    lateinit var binder: ItemBinder

    /** Tags to start with — a tag tapped on an app page, if any. */
    private val initialTags: List<String> by lazy {
        intent.getStringArrayListExtra(EXTRA_TAGS).orEmpty()
    }

    private lateinit var searchView: SearchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val presenterState = savedInstanceState
            ?.getParcelableCompat(KEY_PRESENTER_STATE, ZipParcelable::class.java)
            ?.restore<Bundle>()
        appComponent
            .searchComponent(
                SearchModule(
                    context = this,
                    initialTags = initialTags,
                    state = presenterState
                )
            )
            .inject(activity = this)

        setContentView(R.layout.activity_search)

        setupToolbar()

        val adapter = SimpleRecyclerAdapter(adapterPresenter, binder)
        searchView = SearchViewImpl(window.decorView, adapter)

        presenter.attachView(searchView)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            // The query field fills the toolbar, so there is no room for
            // a title — and nothing to say that the field doesn't.
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onStart() {
        super.onStart()
        presenter.attachRouter(this)
    }

    override fun onStop() {
        presenter.detachRouter()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Arriving with tags already applied means results are on
        // screen; popping the keyboard over them would be in the way.
        if (initialTags.isEmpty()) {
            searchView.requestQueryFocus()
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_PRESENTER_STATE, ZipParcelable(presenter.saveState()))
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun openAppScreen(appId: String, title: String) {
        val intent = com.tomclaw.appsend.screen.details.createDetailsActivityIntent(
            context = this,
            appId = appId,
            label = title,
            moderation = false,
            finishOnly = true
        )
        startActivity(intent)
    }

}

/**
 * Opens search. Pass [tags] to start with those filters applied — the
 * screen is the same either way, they are just criteria it begins with,
 * and the visitor can drop them or add text.
 */
fun createSearchActivityIntent(context: Context, tags: List<String> = emptyList()): Intent =
    Intent(context, SearchActivity::class.java)
        .apply {
            if (tags.isNotEmpty()) {
                putStringArrayListExtra(EXTRA_TAGS, ArrayList(tags))
            }
        }

private const val EXTRA_TAGS = "tags"
private const val KEY_PRESENTER_STATE = "presenter_state"
