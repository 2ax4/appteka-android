package com.tomclaw.appsend.screen.search

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import com.tomclaw.appsend.util.logDebug

/**
 * Where the search history is kept, and nothing else: what is worth
 * keeping, and in what order, is decided in [SearchInteractor].
 */
interface SearchHistoryStorage {

    /** Newest first, and empty when there is nothing kept or nothing readable. */
    fun load(): List<SearchHistoryEntry>

    fun save(entries: List<SearchHistoryEntry>)

}

/**
 * A file of its own next to the cookies, rather than a preference:
 * preferences are read whole into memory on first touch and are meant
 * for settings, not for a list that grows with use.
 *
 * Nothing is held between calls. Search can be opened on top of itself
 * — a tag on an app page reached from a search opens a second one — and
 * two copies each remembering their own history would write over one
 * another's.
 */
class SearchHistoryStorageImpl(
    filesDir: File,
    private val gson: Gson,
) : SearchHistoryStorage {

    private val file = File(filesDir, "search_history.json")

    @Synchronized
    override fun load(): List<SearchHistoryEntry> {
        return try {
            file.takeIf { it.exists() }
                ?.reader()
                ?.use { gson.fromJson<List<SearchHistoryEntry>>(it, ENTRIES_TYPE) }
                .orEmpty()
        } catch (ex: Throwable) {
            // A history nobody can read is a history nobody misses.
            logDebug("[SearchHistory] Error while loading storage: $ex")
            emptyList()
        }
    }

    @Synchronized
    override fun save(entries: List<SearchHistoryEntry>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        try {
            // Written beside the file and moved onto it, so a process
            // killed mid-write leaves the previous history rather than
            // half of the new one.
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writer().use { gson.toJson(entries, it) }
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (ex: Throwable) {
            logDebug("[SearchHistory] Error while saving storage: $ex")
        }
    }

}

private val ENTRIES_TYPE = object : TypeToken<List<SearchHistoryEntry>>() {}.type
