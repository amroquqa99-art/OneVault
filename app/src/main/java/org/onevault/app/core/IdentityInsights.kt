package org.onevault.app.core

/** Pure, offline analytics: inferred registrations and manually confirmed ones stay separate. */
object IdentityInsights {
    data class Registration(val accountId: String, val platformId: String, val category: String, val evidence: String)
    data class CategoryCount(val category: String, val total: Int, val confirmed: Int, val probable: Int)
    data class AccountSummary(val total: Int, val confirmed: Int, val probable: Int, val unknown: Int)

    fun forAccount(id: String, registrations: List<Registration>): AccountSummary {
        val unique = registrations.filter { it.accountId == id }.distinctBy { it.platformId }
        return AccountSummary(
            total = unique.size,
            confirmed = unique.count { it.evidence == "confirmed" },
            probable = unique.count { it.evidence == "probable" },
            unknown = unique.count { it.evidence != "confirmed" && it.evidence != "probable" }
        )
    }

    fun categoryBreakdown(id: String, registrations: List<Registration>): List<CategoryCount> =
        registrations.filter { it.accountId == id }.distinctBy { it.platformId }
            .groupBy { it.category.trim().ifEmpty { "Uncategorized" } }
            .map { (category, data) -> CategoryCount(category, data.size,
                data.count { it.evidence == "confirmed" }, data.count { it.evidence == "probable" }) }
            .sortedWith(compareByDescending<CategoryCount> { it.total }.thenBy { it.category })
}
