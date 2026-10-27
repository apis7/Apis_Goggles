package com.apis.goggles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apis.goggles.data.Action
import com.apis.goggles.data.AppState
import com.apis.goggles.data.BraveApi
import com.apis.goggles.data.Candidate
import com.apis.goggles.data.Coverage
import com.apis.goggles.data.Domains
import com.apis.goggles.data.GitHubApi
import com.apis.goggles.data.Goggle
import com.apis.goggles.data.GoggleEditor
import com.apis.goggles.data.HttpError
import com.apis.goggles.data.Rating
import com.apis.goggles.data.StateStore
import com.apis.goggles.data.SyncState
import com.apis.goggles.data.GoggleState
import com.apis.goggles.data.parseSeedCsv
import com.apis.goggles.data.parseTerms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Rated(val rating: Rating) : UiEvent
}

class RaterViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StateStore(app)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    val selected = MutableStateFlow(Goggle.HEALTH)

    private val _searching = MutableStateFlow<Set<Goggle>>(emptySet())
    val searching = _searching.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val searchJobs = HashMap<Goggle, Job>()
    private val previewTried = HashSet<String>()

    init {
        viewModelScope.launch {
            var s = store.load()
            for (g in Goggle.entries) {
                if (s.of(g).text == null) {
                    val text = store.asset(g.asset)
                    s = s.with(g) { it.copy(text = text) }
                }
                // Bundled terms until GitHub answers; GitHub's copy wins once fetched.
                if (s.of(g).queries.isEmpty() || s.termsVersion < TERMS_VERSION) {
                    val terms = parseTerms(store.asset(g.termsAsset))
                    s = s.with(g) { it.copy(queries = terms) }
                }
            }
            s = s.copy(termsVersion = TERMS_VERSION)
            _state.value = s
            _loaded.value = true
            seedNews()
            refreshFromGitHub()
            Goggle.entries.forEach { prune(it) }
            refill(selected.value)
        }
    }

    private suspend fun update(f: (AppState) -> AppState) = mutex.withLock {
        _state.value = f(_state.value)
        store.save(_state.value)
    }

    private fun emit(text: String) { _events.tryEmit(UiEvent.Message(text)) }

    private suspend fun coverage(g: Goggle, s: AppState = state.value) = withContext(Dispatchers.Default) {
        Coverage(s.of(g).text.orEmpty(), s.ratings.filter { it.goggle == g })
    }

    fun select(g: Goggle) {
        selected.value = g
        refill(g)
    }

    // ---- candidates ---------------------------------------------------------------------------

    /** The news goggle starts with the unrated domains from the Middle East research CSV. */
    private suspend fun seedNews() {
        val g = Goggle.NEWS
        if (state.value.of(g).seeded) return
        val rows = parseSeedCsv(store.asset("news_seed.csv"))
        val cov = coverage(g)
        val seen = HashSet<String>()
        val seeds = withContext(Dispatchers.Default) {
            rows.mapNotNull { (domain, topic) ->
                val d = Domains.registrable(domain)
                if (!seen.add(d) || cov.covers(d)) null else Candidate(d, source = "Seed list · $topic")
            }
        }
        update { s -> s.with(g) { it.copy(queue = it.queue + seeds, seeded = true) } }
    }

    fun refill(g: Goggle) {
        if (searchJobs[g]?.isActive == true) return
        if (state.value.braveKey.isBlank() || state.value.of(g).queue.size >= LOW_WATER) return
        searchJobs[g] = viewModelScope.launch {
            _searching.update { it + g }
            try { searchMore(g) } finally { _searching.update { it - g } }
        }
    }

    private suspend fun searchMore(g: Goggle) {
        var calls = 0
        while (state.value.of(g).queue.size < TARGET_QUEUE && calls < MAX_CALLS_PER_REFILL) {
            val query = pickTerm(state.value.of(g))
            if (query == null) {
                emit("Every ${g.label} search term has been used. Add more to search_terms.xlsx, or start over in Settings.")
                return
            }
            val offset = state.value.of(g).offsets[query] ?: 0
            if (calls > 0) delay(1100) // Brave's free plan allows 1 request/second.
            val hits = try {
                BraveApi.search(state.value.braveKey, query, offset)
            } catch (e: HttpError) {
                emit(if (e.code == 401 || e.code == 422) "Brave rejected the API key. Check Settings." else "Brave search failed (${e.code}).")
                return
            } catch (e: IOException) {
                emit("Search failed: ${e.message ?: "no connection"}")
                return
            }
            calls++
            val cov = coverage(g)
            val found = withContext(Dispatchers.Default) {
                hits.mapNotNull { h -> Domains.host(h.url)?.let { it to h } }
                    .filterNot { (host, _) -> cov.covers(host) }
                    .groupBy({ Domains.registrable(it.first) }, { it.second })
            }
            update { s ->
                val cur = s.of(g)
                val known = cur.queue.map { it.domain }.toSet()
                val fresh = found.filterKeys { it !in known }.map { (d, hs) -> Candidate(d, hs.take(3), query) }
                val next = if (hits.isEmpty()) MAX_OFFSET + 1 else offset + 1
                s.with(g) { it.copy(queue = it.queue + fresh, offsets = it.offsets + (query to next)) }
            }
            prune(g)
        }
    }

    /** A random term, drawn only from those searched the fewest times so every term gets a turn first. */
    private fun pickTerm(gs: GoggleState): String? {
        val open = gs.queries.filter { (gs.offsets[it] ?: 0) <= MAX_OFFSET }
        val least = open.minOfOrNull { gs.offsets[it] ?: 0 } ?: return null
        return open.filter { (gs.offsets[it] ?: 0) == least }.random()
    }

    /** Drops queued sites that are now covered and auto-rates brand siblings of rated sites. */
    private suspend fun prune(g: Goggle) {
        val cov = coverage(g)
        val now = System.currentTimeMillis()
        val (drop, auto) = withContext(Dispatchers.Default) {
            val drop = HashSet<String>()
            val auto = ArrayList<Rating>()
            for (c in state.value.of(g).queue) {
                if (cov.covers(c.domain)) { drop += c.domain; continue }
                val (origin, rule) = cov.sibling(c.domain) ?: continue
                drop += c.domain
                auto += Rating(g, c.domain, rule.action, rule.strength, now, siblingOf = origin, candidate = c)
            }
            drop to auto
        }
        if (drop.isEmpty()) return
        update { s -> s.copy(ratings = s.ratings + auto).with(g) { it.copy(queue = it.queue.filterNot { c -> c.domain in drop }) } }
        auto.forEach { _events.tryEmit(UiEvent.Rated(it)) }
    }

    /** Seed-list sites have no search snippets; fetch a few so the card has something to judge. */
    fun ensurePreview(c: Candidate) {
        val key = state.value.braveKey
        if (c.hits.isNotEmpty() || key.isBlank() || !previewTried.add(c.domain)) return
        viewModelScope.launch {
            val hits = runCatching { BraveApi.search(key, "site:${c.domain}", count = 3) }.getOrNull().orEmpty()
            if (hits.isEmpty()) return@launch
            val g = selected.value
            update { s -> s.with(g) { gs -> gs.copy(queue = gs.queue.map { if (it.domain == c.domain) it.copy(hits = hits) else it }) } }
        }
    }

    // ---- rating -------------------------------------------------------------------------------

    fun rate(c: Candidate, action: Action, strength: Int = 0, brandWide: Boolean = false) {
        val g = selected.value
        viewModelScope.launch {
            val r = Rating(
                g, c.domain, action, strength, System.currentTimeMillis(), candidate = c,
                brandWide = brandWide && action != Action.NEUTRAL && Domains.brandEligible(c.domain),
            )
            update { s ->
                s.copy(ratings = s.ratings.filterNot { it.goggle == g && it.domain == c.domain && it.state == SyncState.PENDING_ADD } + r)
                    .with(g) { it.copy(queue = it.queue.filterNot { q -> q.domain == c.domain }) }
            }
            _events.tryEmit(UiEvent.Rated(r))
            prune(g)
            refill(g)
        }
    }

    fun later(c: Candidate) {
        val g = selected.value
        viewModelScope.launch {
            update { s -> s.with(g) { it.copy(queue = it.queue.filterNot { q -> q.domain == c.domain } + c) } }
        }
    }

    /** Unsynced: forget it and requeue (with any siblings it caused). Synced: queue a removal. */
    fun undo(r: Rating) {
        viewModelScope.launch {
            update { s ->
                when (r.state) {
                    SyncState.PENDING_ADD -> {
                        val gone = s.ratings.filter {
                            it.goggle == r.goggle && it.state == SyncState.PENDING_ADD &&
                                (it.domain == r.domain || it.siblingOf == r.domain)
                        }
                        val back = gone.sortedBy { it.siblingOf != null }.mapNotNull { it.candidate }
                        s.copy(ratings = s.ratings - gone.toSet())
                            .with(r.goggle) { gs -> gs.copy(queue = back + gs.queue.filterNot { q -> back.any { it.domain == q.domain } }) }
                    }
                    SyncState.SYNCED -> s.copy(ratings = s.ratings.map { if (it == r) it.copy(state = SyncState.PENDING_REMOVE) else it })
                    SyncState.PENDING_REMOVE -> s.copy(ratings = s.ratings.map { if (it == r) it.copy(state = SyncState.SYNCED) else it })
                }
            }
        }
    }

    // ---- GitHub -------------------------------------------------------------------------------

    private suspend fun refreshFromGitHub() {
        val s = state.value
        for (g in Goggle.entries) {
            val file = runCatching { GitHubApi.get(s.repo, s.branch, g.repoPath, s.githubToken) }.getOrNull()
            if (file != null) update { st -> st.with(g) { it.copy(text = file.text) } }
            val terms = runCatching { GitHubApi.get(s.repo, s.branch, g.termsPath, s.githubToken) }.getOrNull()
                ?.let { parseTerms(it.text) }?.takeIf { it.isNotEmpty() }
            if (terms != null) update { st -> st.with(g) { it.copy(queries = terms) } }
        }
    }

    fun autoSync() {
        val s = state.value
        if (s.githubToken.isNotBlank() && s.pending().isNotEmpty()) sync()
    }

    fun sync() {
        if (_syncing.value) return
        val s = state.value
        if (s.githubToken.isBlank()) { emit("Add a GitHub token in Settings to sync."); return }
        _syncing.value = true
        viewModelScope.launch {
            var total = 0
            try {
                for (g in Goggle.entries) {
                    val ops = state.value.pending(g)
                    if (ops.isEmpty()) continue
                    val message = "Goggle Rater: ${ops.size} change(s) to ${g.asset}\n\n" +
                        ops.joinToString("\n") { (if (it.state == SyncState.PENDING_REMOVE) "- " else "+ ") + it.ruleLines().joinToString(" ") }
                    try {
                        val text = GitHubApi.update(s.repo, s.branch, g.repoPath, s.githubToken, message) { GoggleEditor.apply(it, ops) }
                        update { st ->
                            st.copy(ratings = st.ratings.mapNotNull { r ->
                                when {
                                    r !in ops -> r
                                    r.state == SyncState.PENDING_REMOVE -> null
                                    else -> r.copy(state = SyncState.SYNCED, candidate = null)
                                }
                            }).with(g) { it.copy(text = text) }
                        }
                        total += ops.size
                    } catch (e: HttpError) {
                        emit(
                            when (e.code) {
                                401, 403 -> "GitHub refused the token. It needs Contents: read & write on ${s.repo}."
                                404 -> "${g.repoPath} isn't in ${s.repo} yet."
                                else -> "Sync failed for ${g.label} (${e.code})."
                            },
                        )
                    } catch (e: IOException) {
                        emit("Sync failed: ${e.message ?: "no connection"}")
                    }
                }
                if (total > 0) emit("Synced $total change(s) to GitHub.")
            } finally {
                _syncing.value = false
            }
        }
    }

    // ---- settings -----------------------------------------------------------------------------

    fun saveSettings(braveKey: String, githubToken: String, repo: String, branch: String) {
        viewModelScope.launch {
            update { s -> s.copy(braveKey = braveKey.trim(), githubToken = githubToken.trim(), repo = repo.trim(), branch = branch.trim()) }
            refreshFromGitHub()
            Goggle.entries.forEach { prune(it) }
            refill(selected.value)
        }
    }

    fun resetPaging(g: Goggle) {
        viewModelScope.launch {
            update { s -> s.with(g) { it.copy(offsets = emptyMap()) } }
            refill(g)
        }
    }

    companion object {
        private const val LOW_WATER = 6
        private const val TARGET_QUEUE = 15
        private const val MAX_CALLS_PER_REFILL = 4
        private const val MAX_OFFSET = 9

        /** Bump to replace stored terms with the bundled ones (v1 had a hard-coded list). */
        private const val TERMS_VERSION = 1
    }
}
