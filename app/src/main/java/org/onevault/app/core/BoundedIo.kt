package org.onevault.app.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.FilterInputStream

/** Portable bounded reads: do not rely on Java 9 InputStream.readNBytes on older Android. */
object BoundedIo {
    /** Streaming size cap; never buffers an entire plaintext password export. */
    fun limit(source: InputStream, maxBytes: Long): InputStream {
        require(maxBytes > 0L)
        return object : FilterInputStream(source) {
            private var consumed=0L
            override fun read(): Int {
                val value=super.read()
                if(value>=0) {consumed++;require(consumed<=maxBytes){"Input exceeds safe byte limit"}}
                return value
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                // InputStream implementations may delegate to read() internally; access the wrapped
                // stream directly here to avoid double-counting.
                val count=`in`.read(b,off,len)
                if(count>0) {consumed+=count;require(consumed<=maxBytes){"Input exceeds safe byte limit"}}
                return count
            }
        }
    }
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
