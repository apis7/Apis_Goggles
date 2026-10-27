package com.apis.goggles.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Domains {
    /** Lower-cased host of [url] without a leading "www.". */
    fun host(url: String): String? = url.toHttpUrlOrNull()?.host?.lowercase()?.removePrefix("www.")

    /** The registrable domain (eTLD+1) via OkHttp's bundled public suffix list: sports.cnn.co.uk → cnn.co.uk. */
    fun registrable(host: String): String = "https://$host/".toHttpUrlOrNull()?.topPrivateDomain() ?: host

    /** The brand label of a registrable domain: cnn.co.uk → cnn. */
    fun brand(domain: String): String = domain.substringBefore('.')

    /** host, then each parent: a.b.cnn.com, b.cnn.com, cnn.com, com. */
    fun selfAndParents(host: String): Sequence<String> =
        generateSequence(host) { d -> d.indexOf('.').takeIf { it >= 0 }?.let { d.substring(it + 1) } }
}
