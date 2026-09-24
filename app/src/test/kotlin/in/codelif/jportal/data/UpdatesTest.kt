package `in`.codelif.jportal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun `versions compare as numbers`() {
        assertTrue(isNewer("0.10.0", "0.9.9"))
        assertTrue(isNewer("v1.0.0", "0.1.0"))
        assertTrue(isNewer("1.2", "1.1.9"))
        assertFalse(isNewer("1.2.0", "1.2"))
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.1.0", "0.2.0"))
        // suffixes don't count either way
        assertFalse(isNewer("0.2.0-rc1", "0.2.0"))
    }

    @Test
    fun `release json reads the tag and page`() {
        val r = parseRelease("""{"tag_name":"v1.4.2","html_url":"https://github.com/x/y/releases/tag/v1.4.2","draft":false,"prerelease":false,"assets":[]}""")
        assertEquals(Release("1.4.2", "https://github.com/x/y/releases/tag/v1.4.2"), r)
    }

    @Test
    fun `drafts, prereleases and junk give nothing`() {
        assertNull(parseRelease("""{"tag_name":"v2.0.0","html_url":"u","draft":false,"prerelease":true}"""))
        assertNull(parseRelease("""{"tag_name":"v2.0.0","html_url":"u","draft":true,"prerelease":false}"""))
        assertNull(parseRelease("""{"message":"Not Found"}"""))
        assertNull(parseRelease(""))
    }
}
