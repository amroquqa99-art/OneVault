package org.onevault.app.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/** Local-only streaming format for user-controlled storage. This is not the Drive pooling feature. */
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
        require(!destination.exists() || (destination.isDirectory && destination.list()?.isEmpty() == true)) {
            "Choose a new or empty chunk directory to prevent overwriting data"
        }
        require(destination.isDirectory || destination.mkdirs()) { "Cannot create chunk directory" }
        val parts = mutableListOf<Part>()
        val created = mutableListOf<File>()
        var index = 0
        try {
            FileInputStream(source).use { input ->
                val buf = ByteArray(minOf(chunkBytes, 1024 * 1024))
                while (true) {
                    val partFile = File(destination, "%08d.part".format(index))
                    created += partFile
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
        } catch (e: Exception) {
            // A failed split must never look like a complete manifest.
            for (part in created) part.delete()
            throw e
        }
    }

    fun assemble(parts: List<Part>, directory: File, output: File, expectedSha256: String) {
        require(parts.isNotEmpty())
        require(parts.map { it.index } == parts.indices.toList()) { "Non-contiguous chunk sequence" }
        require(parts.all { it.filename.matches(Regex("[0-9]{8}\\.part")) })
        require(!Files.exists(output.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Output already exists; refusing to overwrite" }
        val parent = output.absoluteFile.parentFile ?: File(".")
        require(parent.isDirectory) { "Output directory does not exist" }
        val tmp = File.createTempFile("onevault-", ".partial", parent)
        try {
            FileOutputStream(tmp).use { out ->
                for (part in parts) {
                    val src = File(directory, part.filename)
                    require(src.isFile && src.length() == part.bytes && sha256(src) == part.sha256) {
                        "Corrupt or missing part: ${part.index}"
                    }
                    FileInputStream(src).use { it.copyTo(out, 1024 * 1024) }
                }
                out.fd.sync()
            }
            require(sha256(tmp).equals(expectedSha256, ignoreCase = true)) { "Whole-file integrity mismatch" }
            // The hard link is an atomic CREATE_NEW: an existing destination cannot be replaced.
            // Temporary file and destination are in the same directory and filesystem.
            Files.createLink(output.toPath(), tmp.toPath())
        } finally {
            tmp.delete()
        }
    }
}
