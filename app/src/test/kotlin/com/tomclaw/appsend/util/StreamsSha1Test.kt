package com.tomclaw.appsend.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class StreamsSha1Test {

    @Test
    fun `hashes an empty stream`() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", sha1Of(ByteArray(0)))
    }

    @Test
    fun `hashes a short stream`() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1Of("abc".toByteArray()))
    }

    @Test
    fun `hashes a stream longer than the read buffer`() {
        // A million 'a' — the classic SHA-1 vector, and the only case that
        // exercises more than one pass through the buffer
        val input = ByteArray(1_000_000) { 'a'.code.toByte() }

        assertEquals("34aa973cd4c4daa4f61eeb2bdbad27316534016f", sha1Of(input))
    }

    @Test
    fun `closes the stream it was given`() {
        val stream = ByteArrayInputStream("abc".toByteArray())
        var closed = false
        val tracked = object : java.io.FilterInputStream(stream) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        tracked.sha1()

        assertEquals(true, closed)
    }

    private fun sha1Of(bytes: ByteArray): String = ByteArrayInputStream(bytes).sha1()

}
