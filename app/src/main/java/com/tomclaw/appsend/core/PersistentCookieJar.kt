package com.tomclaw.appsend.core

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import com.tomclaw.appsend.util.logDebug

/**
 * Cookies are kept per name, domain and path — the same triple a server
 * overwrites when it re-sets one. A response setting a single cookie has to
 * leave the rest of the jar alone, and a request only gets the cookies that
 * were issued for it.
 */
class PersistentCookieJar(filesDir: File) : CookieJar {

    private val db = File(filesDir, "cookies.dat")
    private var loaded = false
    private val cache = LinkedHashMap<CookieKey, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            // A Set-Cookie that is already expired is how the server drops one,
            // and that is the only expiry we act on — see loadForRequest
            if (cookie.expiresAt <= now) {
                cache.remove(cookie.key())
            } else {
                cache[cookie.key()] = cookie
            }
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        // matches() is where domain, path, secure and host-only live, so the
        // session cookie can't ride along to a download host or another API.
        // Expiry is deliberately left to the server: the session slides with
        // every request while the cookie keeps the date stamped at login, so
        // evicting on it here logs an active user out for good — as would a
        // device clock that runs fast.
        return cache.values.filter { it.matches(url) }
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        loaded = true
        try {
            DataInputStream(FileInputStream(db)).use { input ->
                val size = input.readShort()
                for (i in 0 until size) {
                    val name = input.readUTF()
                    val value = input.readUTF()
                    val expiresAt = input.readLong()
                    val domain = input.readUTF()
                    val path = input.readUTF()
                    val secure = input.readBoolean()
                    val httpOnly = input.readBoolean()
                    val hostOnly = input.readBoolean()
                    val builder = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .expiresAt(expiresAt)
                        .path(path)
                    if (secure) {
                        builder.secure()
                    }
                    if (httpOnly) {
                        builder.httpOnly()
                    }
                    if (hostOnly) {
                        builder.hostOnlyDomain(domain)
                    } else {
                        builder.domain(domain)
                    }
                    val cookie = builder.build()
                    cache[cookie.key()] = cookie
                }
            }
        } catch (ex: Throwable) {
            logDebug("[CookieJar] Error while loading storage: $ex")
        }
    }

    private fun persist() {
        val persistentCookies = cache.values.filter { it.persistent }
        try {
            DataOutputStream(FileOutputStream(db)).use { output ->
                output.writeShort(persistentCookies.size)
                persistentCookies.forEach { cookie ->
                    output.writeUTF(cookie.name)
                    output.writeUTF(cookie.value)
                    output.writeLong(cookie.expiresAt)
                    output.writeUTF(cookie.domain)
                    output.writeUTF(cookie.path)
                    output.writeBoolean(cookie.secure)
                    output.writeBoolean(cookie.httpOnly)
                    output.writeBoolean(cookie.hostOnly)
                }
            }
        } catch (ex: Throwable) {
            logDebug("[CookieJar] Error while saving storage: $ex")
        }
    }

}

private data class CookieKey(
    val name: String,
    val domain: String,
    val path: String,
)

private fun Cookie.key() = CookieKey(name = name, domain = domain, path = path)
