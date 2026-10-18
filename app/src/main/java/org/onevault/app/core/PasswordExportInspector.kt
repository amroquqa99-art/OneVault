package org.onevault.app.core

import java.io.PushbackReader
import java.io.Reader
import java.net.URI

/**
 * Privacy-focused metadata-only Google Password Manager CSV importer.
 *
 * The password and note columns are deliberately NEVER appended to any StringBuilder,
 * model, log or database record. Callers supply a bounded InputStream/Reader and must
 * warn users that the source CSV itself contains plaintext passwords.
 */
object PasswordExportInspector {
    data class Candidate(val email: String, val host: String, val platform: String)
    data class Summary(val candidates: List<Candidate>, val unmatchedRows: Int, val invalidRows: Int)

    fun inspect(source: Reader, registeredEmails: Set<String>, maxRows: Int = 50_000): Summary {
        require(maxRows in 1..100_000)
        val reader = PushbackReader(source.buffered(), 1)
        val header = nextRow(reader, null) ?: error("Empty password CSV")
        val columns = header.map { it.trim().removePrefix("\uFEFF").lowercase() }
        val urlIndex = columns.indexOf("url").takeIf { it >= 0 } ?: error("Missing url column")
        val usernameIndex = columns.indexOf("username").takeIf { it >= 0 } ?: error("Missing username column")
        val nameIndex = columns.indexOf("name")
        val visibleIndices = setOf(urlIndex, usernameIndex) + (if (nameIndex >= 0) setOf(nameIndex) else emptySet())
        val known = registeredEmails.mapTo(mutableSetOf()) { it.trim().lowercase() }
        val candidates = linkedMapOf<Pair<String,String>, Candidate>()
        var rows = 0; var unmatched = 0; var invalid = 0
        while (true) {
            val fields = nextRow(reader,visibleIndices) ?: break
            rows++
            require(rows <= maxRows) { "Password metadata import exceeds row limit" }
            val email = fields.getOrNull(usernameIndex)?.trim()?.lowercase().orEmpty()
            if (email !in known) { unmatched++; continue }
            val host = validHost(fields.getOrNull(urlIndex).orEmpty())
            if (host == null) { invalid++; continue }
            val title = if (nameIndex >= 0) fields.getOrNull(nameIndex).orEmpty().trim().take(100) else ""
            candidates.putIfAbsent(email to host, Candidate(email,host,title.ifBlank{host}))
        }
        return Summary(candidates.values.toList(),unmatched,invalid)
    }

    private fun validHost(raw: String): String? {
        val normalized=raw.trim().takeIf{it.length in 4..2000}?:return null
        val uri = try {URI(if("://" in normalized)normalized else "https://$normalized")}catch(_:Exception){return null}
        if(uri.scheme !in setOf("https","http"))return null
        val host=uri.host?.lowercase()?.removePrefix("www.")?.trimEnd('.')?:return null
        if(host.length !in 4..253 || !host.contains('.') || !host.matches(Regex("[a-z0-9.-]+"))) return null
        if(host.split('.').any { it.isEmpty() || it.startsWith('-') || it.endsWith('-') })return null
        return host
    }

    /** Minimal RFC-4180 parser; captures ONLY approved column indices. */
    private fun nextRow(reader: PushbackReader, keep: Set<Int>?): List<String>? {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var column=0;var quoted=false;var started=false
        fun finishField() {
            fields += if(keep == null || column in keep)current.toString() else ""
            current.setLength(0)
            column++
        }
        fun append(ch:Char) {
            if(keep == null || column in keep) {
                require(current.length < 2048) { "CSV metadata field exceeds safe length" }
                current.append(ch)
            }
        }
        while(true) {
            val code=reader.read()
            if(code < 0) {
                require(!quoted) { "Unterminated quoted CSV field" }
                if(!started)return null
                finishField();return fields
            }
            val ch=code.toChar();started=true
            when {
                ch=='"' && quoted -> {
                    val next=reader.read()
                    if(next=='"'.code) append('"')
                    else {quoted=false;if(next>=0)reader.unread(next)}
                }
                ch=='"' && current.isEmpty() -> quoted=true
                ch==',' && !quoted -> finishField()
                (ch=='\r' || ch=='\n') && !quoted -> {
                    if(ch=='\r') {
                        val next=reader.read()
                        if(next>=0 && next!='\n'.code)reader.unread(next)
                    }
                    finishField();return fields
                }
                else -> append(ch)
            }
        }
    }
}
