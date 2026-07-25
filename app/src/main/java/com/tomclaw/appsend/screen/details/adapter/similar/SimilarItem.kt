package com.tomclaw.appsend.screen.details.adapter.similar

import com.tomclaw.appsend.util.adapter.Item
import com.tomclaw.appsend.screen.details.adapter.similar_app.SimilarAppItem

data class SimilarItem(
    override val id: Long,
    val items: List<SimilarAppItem>,
) : Item
