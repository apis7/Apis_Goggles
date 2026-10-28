package com.apis.goggles.data

import kotlinx.serialization.Serializable

/** Search terms are edited in search_terms.xlsx and exported to each goggle's search_terms.txt. */
@Serializable
enum class Goggle(val label: String, val repoPath: String, val asset: String, val termsPath: String, val termsAsset: String) {
    HEALTH("Health", "Apis_health/apis_health.goggle", "apis_health.goggle", "Apis_health/search_terms.txt", "health_terms.txt"),
    NEWS("News", "Apis_news/apis_news.goggle", "apis_news.goggle", "Apis_news/search_terms.txt", "news_terms.txt"),
}

@Serializable
enum class Action(val label: String) { DISCARD("Discard"), DOWNRANK("Downrank"), NEUTRAL("Neutral"), BOOST("Boost") }

@Serializable
enum class SyncState { PENDING_ADD, SYNCED, PENDING_REMOVE }

@Serializable
data class Hit(val title: String, val url: String, val snippet: String, val favicon: String? = null)

@Serializable
data class Candidate(
    val domain: String,
    val hits: List<Hit> = emptyList(),
    /** The search query (or seed-list topic) that surfaced this site. */
    val source: String = "",
) {
    val fromSearch: Boolean get() = !source.startsWith("Seed list")
}

@Serializable
data class Rating(
    val goggle: Goggle,
    val domain: String,
    val action: Action,
    val strength: Int = 0,
    val at: Long,
    /** Set when the rating was copied from a same-brand domain (cnn.com → cnn.co.uk). */
    val siblingOf: String? = null,
    val state: SyncState = SyncState.PENDING_ADD,
    /** Kept until synced so an undo can put the site back in the queue. */
    val candidate: Candidate? = null,
    /** Cover every `brand.*` domain (cnn.co.uk, edition.cnn.com), not just this one. */
    val brandWide: Boolean = false,
) {
    private val option: String get() = when (action) {
        Action.DISCARD -> "discard"
        Action.DOWNRANK -> "downrank=$strength"
        Action.BOOST -> "boost=$strength"
        Action.NEUTRAL -> ""
    }

    fun ruleLines(): List<String> {
        if (action == Action.NEUTRAL) return listOf("! neutral: $domain")
        if (!brandWide) return listOf("\$$option,site=$domain")
        val brand = Domains.brand(domain)
        return listOf("://$brand.\$$option", ".$brand.\$$option")
    }

    /** "cnn.*" for brand-wide ratings, else the domain. */
    val scope: String get() = if (brandWide) "${Domains.brand(domain)}.*" else domain

    fun describe(): String = when (action) {
        Action.DISCARD, Action.NEUTRAL -> action.label
        else -> "${action.label} $strength"
    }
}

@Serializable
data class GoggleState(
    val queue: List<Candidate> = emptyList(),
    val queries: List<String> = emptyList(),
    /** Next Brave result page (0–9) per query; 10 means exhausted. */
    val offsets: Map<String, Int> = emptyMap(),
    /** Latest goggle text seen on GitHub, or the bundled snapshot. */
    val text: String? = null,
    val seeded: Boolean = false,
)

@Serializable
data class AppState(
    val braveKey: String = "",
    val githubToken: String = "",
    val repo: String = "apis7/Apis_Goggles",
    val branch: String = "main",
    val goggles: Map<Goggle, GoggleState> = emptyMap(),
    val ratings: List<Rating> = emptyList(),
    val termsVersion: Int = 0,
) {
    fun of(g: Goggle): GoggleState = goggles[g] ?: GoggleState()

    fun with(g: Goggle, f: (GoggleState) -> GoggleState): AppState = copy(goggles = goggles + (g to f(of(g))))

    fun pending(g: Goggle? = null): List<Rating> =
        ratings.filter { it.state != SyncState.SYNCED && (g == null || it.goggle == g) }
}
