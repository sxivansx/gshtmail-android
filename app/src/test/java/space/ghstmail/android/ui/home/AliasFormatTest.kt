package space.ghstmail.android.ui.home

import space.ghstmail.android.api.AliasInfo
import space.ghstmail.android.api.AliasStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AliasFormatTest {

    private val now = Instant.parse("2026-11-17T12:00:00Z").toEpochMilli()

    private val aliases = listOf(
        AliasInfo("1", "parcelbird.k7f2q@ghstmail.space", "Parcelbird", true, 2, 2),
        AliasInfo("2", "stackforge.m4x8d@ghstmail.space", "Stackforge", true, 41, 38),
        AliasInfo("3", "nightowl.p2r9c@ghstmail.space", null, false, 6, 1),
    )

    @Test
    fun `no expiry date means never`() {
        assertEquals(Expiry.Never, expiryOf(null, now))
        assertEquals(Expiry.Never, expiryOf("", now))
    }

    @Test
    fun `a date that cannot be read is treated as never`() {
        assertEquals(Expiry.Never, expiryOf("soon", now))
    }

    @Test
    fun `a past date is expired`() {
        assertEquals(Expiry.Expired, expiryOf("2026-11-17T11:59:00Z", now))
        assertEquals(Expiry.Expired, expiryOf("2026-11-17T12:00:00Z", now))
    }

    @Test
    fun `time left is split into days hours and minutes`() {
        assertEquals(Expiry.Left(1, 2, 30), expiryOf("2026-11-18T14:30:00.000Z", now))
        assertEquals(Expiry.Left(0, 3, 5), expiryOf("2026-11-17T15:05:00Z", now))
        assertEquals(Expiry.Left(0, 0, 45), expiryOf("2026-11-17T12:45:59Z", now))
    }

    @Test
    fun `a date with an offset is read too`() {
        assertEquals(Expiry.Left(0, 1, 0), expiryOf("2026-11-17T18:30:00+05:30", now))
    }

    @Test
    fun `search matches the address or the label whatever the case`() {
        assertEquals(listOf("2"), filterAliases(aliases, " STACK ", AliasStatus.All).map { it.id })
        assertEquals(listOf("3"), filterAliases(aliases, "p2r9c", AliasStatus.All).map { it.id })
        assertEquals(emptyList<String>(), filterAliases(aliases, "netflix", AliasStatus.All).map { it.id })
    }

    @Test
    fun `status narrows the list and combines with search`() {
        assertEquals(listOf("1", "2"), filterAliases(aliases, "", AliasStatus.Active).map { it.id })
        assertEquals(listOf("3"), filterAliases(aliases, "", AliasStatus.Paused).map { it.id })
        assertEquals(emptyList<String>(), filterAliases(aliases, "stack", AliasStatus.Paused).map { it.id })
    }

    @Test
    fun `summary counts every alias`() {
        val summary = summarize(aliases)
        assertEquals(3, summary.total)
        assertEquals(2, summary.active)
        assertEquals(49, summary.received)
        assertEquals(41, summary.forwarded)
    }

    @Test
    fun `forward rate is a share of received mail`() {
        assertNull(forwardRate(received = 0, forwarded = 0))
        assertEquals(0.5f, forwardRate(received = 4, forwarded = 2))
        assertEquals(1f, forwardRate(received = 2, forwarded = 5))
    }
}
