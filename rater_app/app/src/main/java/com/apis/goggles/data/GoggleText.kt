package com.apis.goggles.data

data class SiteRule(val action: Action, val strength: Int)

private val NEUTRAL_LINE = Regex("""^!\s*neutral:\s*(\S+)\s*$""", RegexOption.IGNORE_CASE)
private val SECTION = Regex("""^!\s*(DISCARD|DOWNRANK|BOOST|NEUTRAL)\s+LIST\b.*""", RegexOption.IGNORE_CASE)

/** `://abc.` or `.abc.`: the two halves of a brand-wide rule (see tools/brand_rules.py). */
private val BRAND_PATTERN = Regex("""^(?:://|\.)([a-z0-9-]+)\.$""")

/** What a goggle already covers: whole-site rules, brand rules, URL substrings like `*blog*`, and reviewed-neutral sites. */
class GoggleIndex(text: String) {
    val sites = LinkedHashMap<String, SiteRule>()
    val brands = LinkedHashMap<String, SiteRule>()
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
            val pattern = line.substringBefore('$').trim().lowercase()
            val (site, rule) = parseOptions(line.substringAfter('$').substringBefore('$'))
            val brand = BRAND_PATTERN.matchEntire(pattern)?.groupValues?.get(1)
            when {
                site != null && pattern.isEmpty() -> sites.putIfAbsent(site, rule)
                site == null && brand != null -> brands.putIfAbsent(brand, rule)
                site == null && pattern.length > 2 && pattern.startsWith('*') && pattern.endsWith('*') -> {
                    val inner = pattern.trim('*')
                    if (inner.length >= 3 && '/' !in inner && '*' !in inner) patterns += inner
                }
            }
        }
    }

    fun covers(host: String): Boolean {
        val h = host.lowercase()
        return patterns.any { it in h } ||
            brands.keys.any { Domains.hasBrandLabel(h, it) } ||
            Domains.selfAndParents(h).any { it in sites || it in neutral }
    }

    val ruleCount: Int get() = sites.size + brands.size + patterns.size

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
            removeDomain(lines, op)
            if (op.state == SyncState.PENDING_ADD) op.ruleLines().forEach { insert(lines, op.action, it) }
        }
        return lines.joinToString("\n")
    }

    /** Drops every existing rule for the rated site (and its brand, for brand-wide ratings). */
    private fun removeDomain(lines: MutableList<String>, op: Rating) {
        val brand = Domains.brand(op.domain)
        lines.removeAll { raw ->
            val line = raw.trim()
            if (NEUTRAL_LINE.matchEntire(line)?.groupValues?.get(1).equals(op.domain, ignoreCase = true)) return@removeAll true
            if (line.startsWith("!") || '$' !in line) return@removeAll false
            val pattern = line.substringBefore('$')
            (pattern.isEmpty() && line.substringAfter('$').split(',').any { it.trim().equals("site=${op.domain}", ignoreCase = true) }) ||
                (op.brandWide && BRAND_PATTERN.matchEntire(pattern.lowercase())?.groupValues?.get(1) == brand)
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
    private val live = ratings.filter { it.state != SyncState.PENDING_REMOVE }
    private val rated: Set<String> = live.map { it.domain }.toSet()
    private val ratedBrands: Set<String> = live.filter { it.brandWide }.map { Domains.brand(it.domain) }.toSet()
    private val siblings = HashMap<String, Pair<String, SiteRule>?>()

    init {
        // Site-only rules in the goggle still pull same-brand domains along when they turn up.
        // Ratings where you switched brand-wide off are left alone.
        for ((domain, rule) in index.sites) {
            if (Domains.registrable(domain) != domain || !Domains.brandEligible(domain)) continue
            val brand = Domains.brand(domain)
            if (siblings.containsKey(brand)) {
                if (siblings[brand]?.second != rule) siblings[brand] = null // conflicting rules: ask instead
            } else {
                siblings[brand] = domain to rule
            }
        }
    }

    fun covers(host: String): Boolean =
        index.covers(host) ||
            Domains.selfAndParents(host).any { it in rated } ||
            ratedBrands.any { Domains.hasBrandLabel(host, it) }

    /** The already-rated same-brand domain and its rule, e.g. mercola.co.uk → (mercola.com, boost 4). */
    fun sibling(domain: String): Pair<String, SiteRule>? =
        siblings[Domains.brand(domain)]?.takeIf { it.first != domain && Domains.brandEligible(domain) }
}
