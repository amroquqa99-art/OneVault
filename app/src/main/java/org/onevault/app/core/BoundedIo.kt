package org.onevault.app.core

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Portable bounded reads: do not rely on Java 9 InputStream.readNBytes on older Android. */
object BoundedIo {
    fun readAtMost(source: InputStream, maxBytes: Int): ByteArray {
        require(maxBytes >= 0 && maxBytes < Int.MAX_VALUE) { "Invalid byte limit" }
        val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = source.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - total))
            if (count == -1) return output.toByteArray()
            if (count == 0) {
                val single = source.read()
                if (single < 0) return output.toByteArray()
                output.write(single)
                total++
            } else {
                output.write(buffer, 0, count)
                total += count
            }
            require(total <= maxBytes) { "Input exceeds the permitted $maxBytes bytes" }
        }
    }
}
