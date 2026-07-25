package com.tomclaw.appsend.screen.details.adapter.similar

import com.tomclaw.appsend.util.adapter.AdapterPresenter
import com.tomclaw.appsend.util.adapter.ItemPresenter
import com.tomclaw.appsend.screen.details.adapter.ItemListener

class SimilarItemPresenter(
    private val listener: ItemListener,
    private val adapterPresenter: dagger.Lazy<AdapterPresenter>,
) : ItemPresenter<SimilarItemView, SimilarItem>, SimilarAppItemListener {

    override fun bindView(view: SimilarItemView, item: SimilarItem, position: Int) {
        adapterPresenter.get().onDataSourceChanged(item.items)
        view.notifyChanged()
    }

    override fun onSimilarAppClick(appId: String, title: String) {
        listener.onSimilarAppClick(appId, title)
    }

}
