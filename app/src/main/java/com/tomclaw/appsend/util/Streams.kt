package com.tomclaw.appsend.util

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

fun Closeable?.safeClose() {
    try {
        this?.close()
    } catch (ignored: IOException) {
    }
}

/** Reads the stream to the end and closes it. Lower-case hex. */
fun InputStream.sha1(): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteArray(SHA1_BUFFER_SIZE)
    BufferedInputStream(this).use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private const val SHA1_BUFFER_SIZE = 64 * 1024
