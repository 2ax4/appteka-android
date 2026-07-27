package com.tomclaw.appsend.core

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentCookieJarTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val storeUrl = "https://appteka.store/api/1/app/list".toHttpUrl()
    private val otherHostUrl = "https://tomclaw.com/api/appteka/standby".toHttpUrl()
    private val cdnUrl = "https://cdn.example.com/files/app.apk".toHttpUrl()

    @Test
    fun `cookie stays on the host that issued it`() {
        val jar = PersistentCookieJar(folder.newFolder())
        jar.saveFromResponse(storeUrl, listOf(sessionCookie()))

        assertEquals(listOf("session"), jar.loadForRequest(storeUrl).map { it.name })
        assertTrue(jar.loadForRequest(otherHostUrl).isEmpty())
        assertTrue(jar.loadForRequest(cdnUrl).isEmpty())
    }

    @Test
    fun `a cookie set by another host leaves the session alone`() {
        val jar = PersistentCookieJar(folder.newFolder())
        jar.saveFromResponse(storeUrl, listOf(sessionCookie()))

        jar.saveFromResponse(otherHostUrl, listOf(cookie(name = "visit", domain = "tomclaw.com")))

        assertEquals(listOf("session"), jar.loadForRequest(storeUrl).map { it.name })
    }

    @Test
    fun `re-setting a cookie replaces it instead of duplicating`() {
        val jar = PersistentCookieJar(folder.newFolder())
        jar.saveFromResponse(storeUrl, listOf(sessionCookie(value = "old")))

        jar.saveFromResponse(storeUrl, listOf(sessionCookie(value = "new")))

        val cookies = jar.loadForRequest(storeUrl)
        assertEquals(1, cookies.size)
        assertEquals("new", cookies.single().value)
    }

    @Test
    fun `unrelated cookies of the same host survive each other`() {
        val jar = PersistentCookieJar(folder.newFolder())
        jar.saveFromResponse(storeUrl, listOf(sessionCookie()))

        jar.saveFromResponse(storeUrl, listOf(cookie(name = "theme")))

        assertEquals(setOf("session", "theme"), jar.loadForRequest(storeUrl).map { it.name }.toSet())
    }

    @Test
    fun `persistent cookies survive a restart and session ones do not`() {
        val dir = folder.newFolder()
        PersistentCookieJar(dir).saveFromResponse(
            storeUrl,
            listOf(sessionCookie(), cookie(name = "temporary", expiresAt = null)),
        )

        val restarted = PersistentCookieJar(dir)

        assertEquals(listOf("session"), restarted.loadForRequest(storeUrl).map { it.name })
    }

    @Test
    fun `expired cookies are dropped`() {
        val jar = PersistentCookieJar(folder.newFolder())
        jar.saveFromResponse(
            storeUrl,
            listOf(sessionCookie(), cookie(name = "stale", expiresAt = 1_000L)),
        )

        assertEquals(listOf("session"), jar.loadForRequest(storeUrl).map { it.name })
    }

    private fun sessionCookie(value: String = "secret") = cookie(name = "session", value = value)

    private fun cookie(
        name: String,
        value: String = "value",
        domain: String = "appteka.store",
        expiresAt: Long? = FAR_FUTURE,
    ): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .domain(domain)
        .path("/")
        .apply { expiresAt?.let { expiresAt(it) } }
        .build()

}

private const val FAR_FUTURE = 32503680000000L // 2999-01-01
