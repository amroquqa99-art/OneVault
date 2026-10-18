package org.onevault.app.core

/** Byte counts are exact; quota from the provider may include non-Drive services. */
data class StorageBudget(
    val limit: Long,
    val used: Long,
    val reserved: Long,
    val pending: Long = 0L,
) {
    init { require(limit >= 0 && used >= 0 && reserved >= 0 && pending >= 0) }
    val free: Long get() = (limit - used).coerceAtLeast(0)
    val safe: Long get() = (free - reserved - pending).coerceAtLeast(0)
    fun accepts(bytes: Long) = bytes >= 0 && bytes <= safe
}

/** Confidence is an explicit evidence classification, not an assertion of account activity. */
enum class Evidence { CONFIRMED, PROBABLE, UNKNOWN }

object PlatformNames {
    fun normalize(value: String): String = value.trim().lowercase()
        .removePrefix("https://").removePrefix("http://")
        .removePrefix("www.").substringBefore('/').substringBefore('?').trimEnd('.')
}
