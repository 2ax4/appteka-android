package com.tomclaw.appsend.screen.details.adapter.similar

import com.tomclaw.appsend.util.adapter.AdapterPresenter
import com.tomclaw.appsend.util.adapter.ItemPresenter
import com.tomclaw.appsend.screen.details.adapter.ItemListener
import com.tomclaw.appsend.screen.profile.adapter.app.AppItem
import com.tomclaw.appsend.screen.profile.adapter.uploads.AppItemListener

class SimilarItemPresenter(
    private val listener: ItemListener,
    private val adapterPresenter: dagger.Lazy<AdapterPresenter>,
) : ItemPresenter<SimilarItemView, SimilarItem>, AppItemListener {

    override fun bindView(view: SimilarItemView, item: SimilarItem, position: Int) {
        adapterPresenter.get().onDataSourceChanged(item.items)
        view.notifyChanged()
    }

    override fun onAppClick(app: AppItem) {
        listener.onSimilarAppClick(app.appId, app.title)
    }

}
