package com.youtroc.data.extraction.innertube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Synthetic fixture (gate F3 -- no verbatim byte capture exists, only the
 * shape from probe #4581): yt-dlp/NewPipe field order per level segment
 * (`width#height#totalCount#cols#rows#intervalMs#name#sigh`), the real
 * `?sqp=...` query shape, and a `name` field that is ITSELF `M$M` -- the
 * literal placeholder [toStoryboardSpecOrNull] must substitute LAST (gate
 * F4), since it does not exist in the base URL until `$N` is substituted.
 */
class StoryboardSpecParserTest {

    private val baseUrl = "https://i.ytimg.com/sb/aqz-KE-bpKQ/storyboard3_L\$L/\$N.jpg?sqp=-e30%2C"
    private val l0 = "48#27#1#1#1#0#M\$M#rs\$AOn4CLA1" // L0 recap sheet: intervalMs=0, not time-indexed
    private val l1 = "90#45#40#5#8#5000#M\$M#rs\$AOn4CLA2"
    private val l2 = "160#90#107#5#5#2000#M\$M#rs\$AOn4CLA3"

    @Test
    fun `substitutes L then N then M in order, leaving no residual placeholder in a multi-page L2 fixture`() {
        val spec = requireNotNull("$baseUrl|$l0|$l1|$l2".toStoryboardSpecOrNull())
        val pageUrls = requireNotNull(spec.previewLevel()).pageUrls // L2 wins (tallest tile)

        assertTrue(pageUrls[1].contains("/M1.jpg"))
        pageUrls.forEach { url ->
            assertFalse(url.contains("\$L"), "residual \$L in $url")
            assertFalse(url.contains("\$N"), "residual \$N in $url")
            assertFalse(url.contains("\$M"), "residual \$M in $url")
        }
    }

    @Test
    fun `pages a totalFrames of 107 on a 5x5 grid into 5 sheets, rounding up not down`() {
        val spec = requireNotNull("$baseUrl|$l2".toStoryboardSpecOrNull())

        val level = requireNotNull(spec.previewLevel())

        assertEquals(5, level.pageUrls.size) // ceil(107/25) = 5; floor(107/25) = 4 would drop frames 100-106
    }

    @Test
    fun `skips a level segment with fewer than 8 fields or a non-numeric count field, without throwing`() {
        val tooFewFields = "160#90#107#5#5" // 5 fields, not 8
        val nonNumericCount = "160#90#not-a-number#5#5#2000#M\$M#rs\$AOn4CLA3"
        val baselineLevel = requireNotNull("$baseUrl|$l1".toStoryboardSpecOrNull()).previewLevel()

        // l1 stays at original index 0 in every variant below, so the malformed
        // segment appended AFTER it never shifts l1's $L substitution.
        val withTooFew = requireNotNull("$baseUrl|$l1|$tooFewFields".toStoryboardSpecOrNull())
        val withNonNumericCount = requireNotNull("$baseUrl|$l1|$nonNumericCount".toStoryboardSpecOrNull())

        assertEquals(baselineLevel, withTooFew.previewLevel())
        assertEquals(baselineLevel, withNonNumericCount.previewLevel())
    }

    @Test
    fun `returns null overall when only an L0 recap sheet is present, storyboard is not scrub-previewable`() {
        val spec = "$baseUrl|$l0".toStoryboardSpecOrNull()

        assertNull(spec)
    }

    @Test
    fun `returns null overall when the spec string is blank`() {
        val spec = "".toStoryboardSpecOrNull()

        assertNull(spec)
    }
}
