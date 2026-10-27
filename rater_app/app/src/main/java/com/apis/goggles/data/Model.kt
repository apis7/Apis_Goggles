package com.apis.goggles.data

import kotlinx.serialization.Serializable

@Serializable
enum class Goggle(val label: String, val repoPath: String, val asset: String, val defaultQueries: List<String>) {
    HEALTH(
        "Health", "Apis_health/apis_health.goggle", "apis_health.goggle",
        listOf(
            "carnivore diet benefits", "ancestral nutrition", "raw milk health benefits",
            "seed oils inflammation", "vaccine injury research", "natural cancer treatment",
            "chiropractic care benefits", "autoimmune protocol diet", "grass fed beef nutrition",
            "functional medicine doctor", "metabolic health insulin resistance", "fluoride toxicity",
            "holistic dentistry", "herbal remedies", "organ meats nutrition", "long covid treatment",
        ),
    ),
    NEWS(
        "News", "Apis_news/apis_news.goggle", "apis_news.goggle",
        listOf(
            "US Iran negotiations", "War in Lebanon news", "How was the State of Israel established",
            "History of Antisemitism", "Jewish population before and after World War II",
            "Gaza ceasefire news", "Ukraine war news", "Syria news", "Yemen Red Sea shipping",
            "independent journalism", "big tech censorship", "Federal Reserve interest rates",
            "election integrity news", "foreign policy analysis",
        ),
    ),
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
)

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
) {
    fun ruleLine(): String = when (action) {
        Action.DISCARD -> "\$discard,site=$domain"
        Action.DOWNRANK -> "\$downrank=$strength,site=$domain"
        Action.BOOST -> "\$boost=$strength,site=$domain"
        Action.NEUTRAL -> "! neutral: $domain"
    }

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
    val nextQuery: Int = 0,
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
) {
    fun of(g: Goggle): GoggleState = goggles[g] ?: GoggleState(queries = g.defaultQueries)

    fun with(g: Goggle, f: (GoggleState) -> GoggleState): AppState = copy(goggles = goggles + (g to f(of(g))))

    fun pending(g: Goggle? = null): List<Rating> =
        ratings.filter { it.state != SyncState.SYNCED && (g == null || it.goggle == g) }
}
