package org.onevault.app.core

/** Logic shared by the Android worker and JVM tests. No quota estimate is trusted when absent. */
object TransferPolicy {
    fun safeForNewUpload(quota: StorageBudget?, requestedBytes: Long, earlierPendingBytes: Long): Boolean {
        require(requestedBytes >= 0L && earlierPendingBytes >= 0L)
        if (quota == null || quota.limit <= 0L) return false
        return quota.copy(pending = earlierPendingBytes).accepts(requestedBytes)
    }
}

/** Strict parser for Google's resumable upload Range response header. */
object ResumableRanges {
    private val pattern = Regex("bytes=(\\d+)-(\\d+)")

    /** Null means no acknowledged range; malformed data is never treated as success. */
    fun acknowledgedBytes(header: String?, totalBytes: Long): Long? {
        require(totalBytes >= 0L)
        if (header == null) return null
        val match = pattern.matchEntire(header.trim()) ?: throw IllegalArgumentException("Malformed upload range")
        val first = match.groupValues[1].toLongOrNull() ?: throw IllegalArgumentException("Invalid range start")
        val last = match.groupValues[2].toLongOrNull() ?: throw IllegalArgumentException("Invalid range end")
        require(first == 0L && last >= 0L && last < totalBytes) { "Out-of-bounds upload range" }
        return last + 1L
    }
}

/** Stronger evidence must not be replaced by a weaker mail-import inference. */
object RegistrationEvidence {
    private val ranking = mapOf("unknown" to 0, "probable" to 1, "confirmed" to 2)

    fun keepStronger(existing: String, incoming: String): String {
        val old = ranking[existing] ?: error("Invalid old evidence level")
        val new = ranking[incoming] ?: error("Invalid new evidence level")
        return if (old >= new) existing else incoming
    }
}
