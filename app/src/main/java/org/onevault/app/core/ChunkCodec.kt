package org.onevault.app.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/** Local streaming format prototype for user-controlled / compatible backends only. */
object ChunkCodec {
    data class Part(val index: Int, val filename: String, val bytes: Long, val sha256: String)
    data class Result(val sha256: String, val totalBytes: Long, val parts: List<Part>)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun split(source: File, destination: File, chunkBytes: Int = 16 * 1024 * 1024): Result {
        require(chunkBytes in (256 * 1024)..(256 * 1024 * 1024))
        require(source.isFile && destination.canonicalPath != source.canonicalPath)
        destination.mkdirs()
        val parts = mutableListOf<Part>()
        var index = 0
        FileInputStream(source).use { input ->
            val buf = ByteArray(minOf(chunkBytes, 1024 * 1024))
            while (true) {
                val partFile = File(destination, "%08d.part".format(index))
                var remaining = chunkBytes.toLong()
                var written = 0L
                FileOutputStream(partFile).use { output ->
                    while (remaining > 0) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        output.write(buf, 0, n)
                        remaining -= n
                        written += n
                    }
                    output.fd.sync()
                }
                if (written == 0L) { partFile.delete(); break }
                parts += Part(index, partFile.name, written, sha256(partFile))
                index++
            }
        }
        return Result(sha256(source), source.length(), parts)
    }

    fun assemble(parts: List<Part>, directory: File, output: File, expectedSha256: String) {
        require(parts.isNotEmpty())
        require(parts.map { it.index } == parts.indices.toList()) { "Non-contiguous chunk sequence" }
        require(parts.all { it.filename.matches(Regex("[0-9]{8}\\.part")) })
        val tmp = File(output.parentFile ?: File("."), output.name + ".partial")
        require(tmp.canonicalPath != output.canonicalPath)
        try {
            FileOutputStream(tmp).use { out ->
                for (part in parts) {
                    val src = File(directory, part.filename)
                    require(src.length() == part.bytes && sha256(src) == part.sha256) { "Corrupt part: ${part.index}" }
                    FileInputStream(src).use { it.copyTo(out, 1024 * 1024) }
                }
                out.fd.sync()
            }
            require(sha256(tmp).equals(expectedSha256, ignoreCase = true)) { "Whole-file integrity mismatch" }
            require(tmp.renameTo(output)) { "Atomic publish failed" }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}
