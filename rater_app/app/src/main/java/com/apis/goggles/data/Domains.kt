package com.apis.goggles.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Domains {
    /** Lower-cased host of [url] without a leading "www.". */
    fun host(url: String): String? = url.toHttpUrlOrNull()?.host?.lowercase()?.removePrefix("www.")

    /** The registrable domain (eTLD+1) via OkHttp's bundled public suffix list: sports.cnn.co.uk → cnn.co.uk. */
    fun registrable(host: String): String = "https://$host/".toHttpUrlOrNull()?.topPrivateDomain() ?: host

    /** The brand label of a registrable domain: cnn.co.uk → cnn. */
    fun brand(domain: String): String = domain.substringBefore('.')

    /**
     * True when a rule for [domain] can safely cover every `brand.*` domain: the domain is a bare
     * name under a country or generic TLD (cnn.com, abc.net.au) and the name isn't a common word.
     * Hosted names like someone.blogspot.com never qualify.
     */
    fun brandEligible(domain: String): Boolean {
        val labels = domain.split('.')
        val plain = labels.size == 2 || (labels.size == 3 && labels[1] in SLDS && labels[2].length == 2)
        val brand = labels[0]
        return plain && brand.length >= 3 && brand !in GENERIC_BRANDS
    }

    /** True when [brand] is a host label other than the TLD: abc.co.uk, news.abc.com. */
    fun hasBrandLabel(host: String, brand: String): Boolean = host.split('.').dropLast(1).contains(brand)

    /** host, then each parent: a.b.cnn.com, b.cnn.com, cnn.com, com. */
    fun selfAndParents(host: String): Sequence<String> =
        generateSequence(host) { d -> d.indexOf('.').takeIf { it >= 0 }?.let { d.substring(it + 1) } }

    private val SLDS = setOf("co", "com", "org", "net", "gov", "ac", "edu", "or", "ne", "go")

    /** Names too common to mean one publisher across TLDs. Mirrored in tools/brand_rules.py. */
    val GENERIC_BRANDS = setOf(
        "news", "health", "time", "times", "people", "the", "get", "web", "online", "info", "blog",
        "home", "app", "mail", "shop", "store", "world", "daily", "live", "media", "radio", "net",
        "site", "post", "press", "today", "life", "food", "diet", "med", "medical", "doctor",
        "mind", "boston", "who", "acs", "lung", "kidney", "arthritis", "alz",
    )
}
