package com.apis.goggles.data

data class SiteRule(val action: Action, val strength: Int)

private val NEUTRAL_LINE = Regex("""^!\s*neutral:\s*(\S+)\s*$""", RegexOption.IGNORE_CASE)
private val SECTION = Regex("""^!\s*(DISCARD|DOWNRANK|BOOST|NEUTRAL)\s+LIST\b.*""", RegexOption.IGNORE_CASE)

/** What a goggle already covers: whole-site rules, URL substrings like `*cnn*`, and reviewed-neutral sites. */
class GoggleIndex(text: String) {
    val sites = LinkedHashMap<String, SiteRule>()
    val patterns = ArrayList<String>()
    val neutral = HashSet<String>()

    init {
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val neutralMatch = NEUTRAL_LINE.matchEntire(line)
            if (neutralMatch != null) {
                neutral += neutralMatch.groupValues[1].lowercase()
                continue
            }
            if (line.isEmpty() || line.startsWith("!") || '$' !in line) continue
            val pattern = line.substringBefore('$').trim()
            val (site, rule) = parseOptions(line.substringAfter('$').substringBefore('$'))
            if (site != null && pattern.isEmpty()) {
                sites.putIfAbsent(site, rule)
            } else if (site == null && pattern.length > 2 && pattern.startsWith('*') && pattern.endsWith('*')) {
                val inner = pattern.trim('*').lowercase()
                if (inner.length >= 3 && '/' !in inner && '*' !in inner) patterns += inner
            }
        }
    }

    fun covers(host: String): Boolean {
        val h = host.lowercase()
        return patterns.any { it in h } || Domains.selfAndParents(h).any { it in sites || it in neutral }
    }

    val ruleCount: Int get() = sites.size + patterns.size

    private fun parseOptions(opts: String): Pair<String?, SiteRule> {
        var action = Action.BOOST
        var strength = 1
        var site: String? = null
        for (o in opts.split(',')) {
            val key = o.substringBefore('=').trim().lowercase()
            val value = o.substringAfter('=', "").trim()
            when (key) {
                "boost" -> { action = Action.BOOST; strength = value.toIntOrNull() ?: 1 }
                "downrank" -> { action = Action.DOWNRANK; strength = value.toIntOrNull() ?: 1 }
                "discard" -> { action = Action.DISCARD; strength = 0 }
                "site" -> site = value.lowercase()
            }
        }
        return site to SiteRule(action, strength)
    }
}

/** Applies pending ratings to goggle text, filing each rule under its section header. */
object GoggleEditor {
    fun apply(text: String, ops: List<Rating>): String {
        val lines = text.lines().toMutableList()
        for (op in ops) {
            removeDomain(lines, op.domain)
            if (op.state == SyncState.PENDING_ADD) insert(lines, op.action, op.ruleLine())
        }
        return lines.joinToString("\n")
    }

    private fun removeDomain(lines: MutableList<String>, domain: String) {
        lines.removeAll { raw ->
            val line = raw.trim()
            NEUTRAL_LINE.matchEntire(line)?.groupValues?.get(1).equals(domain, ignoreCase = true) ||
                (!line.startsWith("!") && line.startsWith("$") &&
                    line.substringAfter('$').split(',').any { it.trim().equals("site=$domain", ignoreCase = true) })
        }
    }

    private fun insert(lines: MutableList<String>, action: Action, rule: String) {
        val header = lines.indexOfFirst { SECTION.matchEntire(it.trim())?.groupValues?.get(1).equals(action.name, true) }
        if (header < 0) {
            while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
            lines += listOf("", "! ${action.name} LIST", "", rule, "")
            return
        }
        var end = (header + 1 until lines.size).firstOrNull { SECTION.matches(lines[it].trim()) } ?: lines.size
        while (end - 1 > header && lines[end - 1].isBlank()) end--
        lines.add(end, rule)
    }
}

/** Everything that should never be asked about again for one goggle, plus brand-sibling lookup. */
class Coverage(text: String, ratings: List<Rating>) {
    val index = GoggleIndex(text)
    private val rated: Set<String> = ratings.filter { it.state != SyncState.PENDING_REMOVE }.map { it.domain }.toSet()
    private val brands = HashMap<String, Pair<String, SiteRule>?>()

    init {
        fun add(domain: String, rule: SiteRule) {
            if (rule.action == Action.NEUTRAL || Domains.registrable(domain) != domain) return
            val brand = Domains.brand(domain)
            if (brand.length < 3 || brand in GENERIC_BRANDS) return
            // Two different rules for one brand are ambiguous: ask instead of guessing.
            if (brands.containsKey(brand)) {
                if (brands[brand]?.second != rule) brands[brand] = null
            } else {
                brands[brand] = domain to rule
            }
        }
        index.sites.forEach { (d, r) -> add(d, r) }
        ratings.filter { it.state != SyncState.PENDING_REMOVE }.forEach { add(it.domain, SiteRule(it.action, it.strength)) }
    }

    fun covers(host: String): Boolean = index.covers(host) || Domains.selfAndParents(host).any { it in rated }

    /** The already-rated same-brand domain and its rule, e.g. cnn.co.uk → (cnn.com, discard). */
    fun sibling(domain: String): Pair<String, SiteRule>? =
        brands[Domains.brand(domain)]?.takeIf { it.first != domain }

    companion object {
        /** Brand labels too generic to imply the same publisher across TLDs. */
        val GENERIC_BRANDS = setOf(
            "news", "health", "time", "times", "people", "the", "get", "web", "online", "info", "blog",
            "home", "app", "mail", "shop", "store", "world", "daily", "live", "media", "radio", "net",
            "site", "post", "press", "today", "life", "food", "diet", "med", "medical", "doctor",
        )
    }
}
