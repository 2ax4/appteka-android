package com.tomclaw.appsend.screen.search

import com.tomclaw.appsend.core.StoreApi
import com.tomclaw.appsend.util.SchedulersFactory
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale

/**
 * The rules a search history is kept by: what is worth remembering, what
 * counts as the same search twice, and how deep the history goes. They
 * live in the interactor rather than in the storage, which only reads
 * and writes the file it is given.
 *
 * StoreApi is a 60-method interface; as in UserCapabilitiesInteractorTest
 * a JDK dynamic proxy stands in for it and shouts if the history ever
 * reaches for the network.
 */
class SearchHistoryInteractorTest {

    private val storage = FakeSearchHistoryStorage()
    private val interactor = SearchInteractorImpl(
        api = offlineApi(),
        historyStorage = storage,
        locale = Locale.ENGLISH,
        schedulers = immediateSchedulers(),
    )

    @Test
    fun `the history is read straight from storage`() {
        storage.save(listOf(entry("telegram")))

        assertEquals(listOf(entry("telegram")), interactor.loadHistory().blockingFirst())
    }

    @Test
    fun `a remembered search goes to the top`() {
        remember("telegram")
        val history = remember("browser")

        assertEquals(listOf(entry("browser"), entry("telegram")), history)
        assertEquals(history, storage.stored)
    }

    @Test
    fun `the same search asked twice is one entry`() {
        remember("telegram")
        remember("browser")
        val history = remember("telegram")

        assertEquals(listOf(entry("telegram"), entry("browser")), history)
    }

    @Test
    fun `case is not what makes two searches different`() {
        remember("telegram")
        val history = remember("Telegram")

        assertEquals(listOf(entry("Telegram")), history)
    }

    @Test
    fun `the steps a query was typed through give way to the query`() {
        remember("tele")
        remember("telegr")
        val history = remember("telegram")

        assertEquals(listOf(entry("telegram")), history)
    }

    @Test
    fun `an older search that reads as a prefix stays`() {
        remember("tele")
        remember("browser")
        val history = remember("telegram")

        assertEquals(listOf(entry("telegram"), entry("browser"), entry("tele")), history)
    }

    @Test
    fun `a prefix filtered by other tags is a search of its own`() {
        remember("tele", "tools")
        val history = remember("telegram")

        assertEquals(listOf(entry("telegram"), entry("tele", "tools")), history)
    }

    @Test
    fun `tags in another order are the same tags`() {
        remember("chat", "tools", "social")
        val history = remember("chat", "social", "tools")

        assertEquals(listOf(entry("chat", "social", "tools")), history)
    }

    @Test
    fun `a single letter is not worth remembering`() {
        val history = remember("t")

        assertEquals(emptyList<SearchHistoryEntry>(), history)
        assertEquals(0, storage.saves)
    }

    @Test
    fun `a single letter narrowed by a tag is worth remembering`() {
        val history = remember("t", "tools")

        assertEquals(listOf(entry("t", "tools")), history)
    }

    @Test
    fun `a search made of tags alone is remembered`() {
        val history = remember("", "tools")

        assertEquals(listOf(entry("", "tools")), history)
    }

    @Test
    fun `spacing around a query is not part of it`() {
        val history = remember("  telegram  ")

        assertEquals(listOf(entry("telegram")), history)
    }

    @Test
    fun `the history stops at twenty searches`() {
        repeat(25) { remember("query $it") }

        assertEquals(20, storage.stored.size)
        assertEquals(entry("query 24"), storage.stored.first())
    }

    @Test
    fun `removing a search leaves the rest in order`() {
        remember("telegram")
        remember("browser")

        val history = interactor.removeFromHistory(entry("telegram")).blockingFirst()

        assertEquals(listOf(entry("browser")), history)
        assertEquals(history, storage.stored)
    }

    @Test
    fun `clearing leaves nothing`() {
        remember("telegram")

        val history = interactor.clearHistory().blockingFirst()

        assertEquals(emptyList<SearchHistoryEntry>(), history)
        assertEquals(emptyList<SearchHistoryEntry>(), storage.stored)
    }

    // --- helpers ----------------------------------------------------

    private fun remember(query: String, vararg tags: String): List<SearchHistoryEntry> =
        interactor.addToHistory(query, tags.toList()).blockingFirst()

    private fun entry(query: String, vararg tags: String) =
        SearchHistoryEntry(query, tags.toList())

    private fun offlineApi(): StoreApi {
        val handler = InvocationHandler { _, method: Method, _ ->
            error("unexpected StoreApi call from the history: ${method.name}")
        }
        return Proxy.newProxyInstance(
            StoreApi::class.java.classLoader,
            arrayOf(StoreApi::class.java),
            handler,
        ) as StoreApi
    }

    private fun immediateSchedulers(): SchedulersFactory = object : SchedulersFactory {
        override fun io(): Scheduler = Schedulers.trampoline()
        override fun mainThread(): Scheduler = Schedulers.trampoline()
    }

}

private class FakeSearchHistoryStorage : SearchHistoryStorage {

    var stored: List<SearchHistoryEntry> = emptyList()
        private set
    var saves: Int = 0
        private set

    override fun load(): List<SearchHistoryEntry> = stored

    override fun save(entries: List<SearchHistoryEntry>) {
        stored = entries
        saves++
    }

}
