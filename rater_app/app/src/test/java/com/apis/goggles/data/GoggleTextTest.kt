package com.apis.goggles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoggleTextTest {
    private val goggle = """
        ! name: Test
        ! DISCARD LIST

        *cnn*${'$'}discard,inurl
        ${'$'}discard,site=nytimes.com

        ! DOWNRANK LIST

        ${'$'}downrank=2,site=medium.com

        ! BOOST LIST

        ${'$'}boost=3,site=substack.com

        ! NEUTRAL LIST (reviewed, deliberately no rule)

        ! neutral: aljazeera.com
    """.trimIndent()

    private fun rating(domain: String, action: Action, strength: Int = 0, state: SyncState = SyncState.PENDING_ADD) =
        Rating(Goggle.NEWS, domain, action, strength, at = 1, state = state)

    @Test fun coversSitesSubdomainsPatternsAndNeutral() {
        val cov = Coverage(goggle, emptyList())
        assertTrue(cov.covers("nytimes.com"))
        assertTrue(cov.covers("cooking.nytimes.com"))
        assertTrue(cov.covers("edition.cnn.com"))
        assertTrue(cov.covers("aljazeera.com"))
        assertFalse(cov.covers("example.com"))
        assertFalse(cov.covers("notnytimes.com"))
    }

    @Test fun siteRulesPullSiblingTldsAlong() {
        val cov = Coverage(goggle, emptyList())
        assertEquals("nytimes.com" to SiteRule(Action.DISCARD, 0), cov.sibling("nytimes.co.uk"))
        assertNull(cov.sibling("nytimes.com"))
    }

    @Test fun genericBrandsAreNotSiblings() {
        val cov = Coverage("\$discard,site=health.com", emptyList())
        assertNull(cov.sibling("health.gov.au"))
    }

    @Test fun brandRulesCoverEveryTldAndSubdomain() {
        val cov = Coverage("://abc.\$discard\n.abc.\$discard", emptyList())
        assertTrue(cov.covers("abc.com"))
        assertTrue(cov.covers("abc.co.uk"))
        assertTrue(cov.covers("news.abc.net.au"))
        assertFalse(cov.covers("abcam.com"))
        assertFalse(cov.covers("example.abc"))
    }

    @Test fun brandWideRatingWritesBrandRulesAndCoversSiblings() {
        val r = rating("dailywire.com", Action.DOWNRANK, 3).copy(brandWide = true)
        assertEquals(listOf("://dailywire.\$downrank=3", ".dailywire.\$downrank=3"), r.ruleLines())
        val cov = Coverage("", listOf(r))
        assertTrue(cov.covers("dailywire.co.uk"))
        val out = GoggleEditor.apply(goggle, listOf(r))
        assertTrue("://dailywire.\$downrank=3" in out)
        // Re-rating the brand replaces both lines.
        val again = GoggleEditor.apply(out, listOf(r.copy(action = Action.DISCARD)))
        assertFalse("downrank=3" in again)
        assertTrue("://dailywire.\$discard" in again)
    }

    @Test fun brandEligibility() {
        assertTrue(Domains.brandEligible("cnn.com"))
        assertTrue(Domains.brandEligible("abc.net.au"))
        assertFalse(Domains.brandEligible("someone.blogspot.com"))
        assertFalse(Domains.brandEligible("health.com"))
        assertFalse(Domains.brandEligible("ox.ac.uk"))
    }

    @Test fun registrableHandlesMultiPartSuffixes() {
        assertEquals("bbc.co.uk", Domains.registrable("news.bbc.co.uk"))
        assertEquals("cnn.com", Domains.registrable("edition.cnn.com"))
    }

    @Test fun editorFilesRulesUnderTheirSections() {
        val out = GoggleEditor.apply(
            goggle,
            listOf(
                rating("foo.com", Action.BOOST, 5),
                rating("bar.org", Action.DISCARD),
                rating("baz.net", Action.NEUTRAL),
                rating("medium.com", Action.DOWNRANK, 4),
            ),
        ).lines()
        fun section(name: String): List<String> {
            val start = out.indexOfFirst { it.startsWith("! $name LIST") }
            val end = (start + 1 until out.size).firstOrNull { Regex("^! \\w+ LIST.*").matches(out[it]) } ?: out.size
            return out.subList(start, end)
        }
        assertTrue("\$boost=5,site=foo.com" in section("BOOST"))
        assertTrue("\$discard,site=bar.org" in section("DISCARD"))
        assertTrue("! neutral: baz.net" in section("NEUTRAL"))
        assertEquals(listOf("\$downrank=4,site=medium.com"), section("DOWNRANK").filter { "medium" in it })
        // Rules land right after the section's last rule, not after its trailing blank line.
        val discard = section("DISCARD")
        assertEquals("\$discard,site=bar.org", discard[discard.indexOfLast { it.isNotBlank() }])
    }

    @Test fun editorRemovesSyncedRules() {
        val out = GoggleEditor.apply(goggle, listOf(rating("substack.com", Action.BOOST, 3, SyncState.PENDING_REMOVE)))
        assertFalse("substack.com" in out)
        assertTrue("*cnn*" in out)
    }

    @Test fun seedCsvKeepsOnlyUnratedRows() {
        val csv = "rank,source_domain,primary_topic,\"Weight (1 = normal, 0=block)\"\n1,a.com,Topic,0\n2,b.com,Topic,\n"
        assertEquals(listOf("b.com" to "Topic"), parseSeedCsv(csv))
    }
}
