package org.onevault.app.core

/**
 * Deterministic, offline, opt-in hints for categorizing imported service domains.
 * These are heuristics, not proof of an account or a current service registration.
 */
object PlatformClassifier {
    private val known = mapOf(
        "github.com" to "Development",
        "gitlab.com" to "Development",
        "bitbucket.org" to "Development",
        "vercel.com" to "Development",
        "replit.com" to "Development",
        "stackoverflow.com" to "Development",
        "notion.so" to "Productivity",
        "notion.com" to "Productivity",
        "todoist.com" to "Productivity",
        "trello.com" to "Productivity",
        "asana.com" to "Productivity",
        "clickup.com" to "Productivity",
        "airtable.com" to "Productivity",
        "figma.com" to "Design",
        "canva.com" to "Design",
        "adobe.com" to "Design",
        "behance.net" to "Design",
        "dribbble.com" to "Design",
        "linkedin.com" to "Professional",
        "coursera.org" to "Education",
        "edx.org" to "Education",
        "udemy.com" to "Education",
        "khanacademy.org" to "Education",
        "chess.com" to "Games",
        "lichess.org" to "Games",
        "netflix.com" to "Entertainment",
        "spotify.com" to "Entertainment",
        "discord.com" to "Communication",
        "slack.com" to "Communication",
        "zoom.us" to "Communication",
        "openai.com" to "AI",
        "anthropic.com" to "AI",
        "huggingface.co" to "AI",
        "perplexity.ai" to "AI"
    )

    fun classify(host: String): String {
        val domain = PlatformNames.normalize(host)
        return known.entries.firstOrNull { (suffix, _) ->
            domain == suffix || domain.endsWith(".$suffix")
        }?.value ?: "Uncategorized"
    }
}
