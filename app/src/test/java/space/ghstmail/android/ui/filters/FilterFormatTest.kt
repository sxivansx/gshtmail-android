package space.ghstmail.android.ui.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterFormatTest {

    @Test
    fun `a typed domain is trimmed and lowercased`() {
        assertEquals("spam.com", normalizeDomain("  Spam.COM "))
    }

    @Test
    fun `a sender address counts as its domain`() {
        assertEquals("spam.com", normalizeDomain("news@spam.com"))
        assertEquals("spam.com", normalizeDomain("@spam.com"))
    }

    @Test
    fun `a trailing dot is dropped`() {
        assertEquals("mail.spam.com", normalizeDomain("mail.spam.com."))
    }

    @Test
    fun `a domain needs a dot and no gaps`() {
        assertTrue(looksLikeDomain("spam.com"))
        assertTrue(looksLikeDomain("mail.spam.co.in"))
        assertFalse(looksLikeDomain("spam"))
        assertFalse(looksLikeDomain("spam .com"))
        assertFalse(looksLikeDomain("spam..com"))
        assertFalse(looksLikeDomain(""))
    }

    @Test
    fun `a web address is not a domain`() {
        assertFalse(looksLikeDomain(normalizeDomain("https://spam.com/unsubscribe")))
    }
}
