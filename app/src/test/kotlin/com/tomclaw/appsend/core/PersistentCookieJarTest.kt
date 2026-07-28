package com.tomclaw.appsend.core

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class PersistentCookieJarTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val storeUrl = "https://appteka.store/api/1/app/list".toHttpUrl()
    private val uploadUrl = "https://appteka.store/api/1/app/upload".toHttpUrl()
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
    fun `a cookie that arrives expired is dropped`() {
        val jar = PersistentCookieJar(folder.newFolder())
        jar.saveFromResponse(
            storeUrl,
            listOf(sessionCookie(), cookie(name = "stale", expiresAt = 1_000L)),
        )

        assertEquals(listOf("session"), jar.loadForRequest(storeUrl).map { it.name })
    }

    @Test
    fun `the server expiring a cookie removes it for good`() {
        val dir = folder.newFolder()
        val jar = PersistentCookieJar(dir)
        jar.saveFromResponse(storeUrl, listOf(sessionCookie()))

        // what a logout sends back
        jar.saveFromResponse(storeUrl, listOf(Cookie.parse(storeUrl, "session=; Path=/; Max-Age=0")!!))

        assertTrue(jar.loadForRequest(storeUrl).isEmpty())
        assertTrue(PersistentCookieJar(dir).loadForRequest(storeUrl).isEmpty())
    }

    // The session slides on the server with every request while the cookie keeps
    // the expiry stamped at login, so a date that has run out is not the client's
    // call to act on — nor is a device clock that runs fast.
    @Test
    fun `a stored cookie past its expiry is still sent`() {
        val dir = folder.newFolder()
        writeJar(dir, sessionCookie(expiresAt = System.currentTimeMillis() - 24 * 60 * 60 * 1000L))

        val jar = PersistentCookieJar(dir)

        assertEquals(listOf("session"), jar.loadForRequest(storeUrl).map { it.name })
        assertEquals(listOf("session"), jar.loadForRequest(uploadUrl).map { it.name })
    }

    /** Writes [cookies] in the jar's own on-disk format. */
    private fun writeJar(dir: File, vararg cookies: Cookie) {
        DataOutputStream(FileOutputStream(File(dir, "cookies.dat"))).use { output ->
            output.writeShort(cookies.size)
            cookies.forEach { cookie ->
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
    }

    private fun sessionCookie(value: String = "secret", expiresAt: Long = FAR_FUTURE) =
        cookie(name = "session", value = value, expiresAt = expiresAt)

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
