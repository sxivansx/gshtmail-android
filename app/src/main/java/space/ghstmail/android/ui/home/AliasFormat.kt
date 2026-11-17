package space.ghstmail.android.ui.home

import space.ghstmail.android.api.AliasInfo
import space.ghstmail.android.api.AliasStatus
import space.ghstmail.android.api.AliasSummary
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

sealed interface Expiry {
    data object Never : Expiry
    data object Expired : Expiry
    data class Left(val days: Long, val hours: Long, val minutes: Long) : Expiry
}

fun expiryOf(expiresAt: String?, nowMillis: Long): Expiry {
    if (expiresAt.isNullOrBlank()) return Expiry.Never
    val at = parseInstant(expiresAt) ?: return Expiry.Never
    val left = at.toEpochMilli() - nowMillis
    if (left <= 0) return Expiry.Expired
    val minutes = left / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return Expiry.Left(days = days, hours = hours % 24, minutes = minutes % 60)
}

private fun parseInstant(text: String): Instant? = try {
    Instant.parse(text)
} catch (_: DateTimeParseException) {
    try {
        OffsetDateTime.parse(text).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** The filtering a paging server does for us, for one that sent every alias instead. */
fun filterAliases(aliases: List<AliasInfo>, query: String, status: AliasStatus): List<AliasInfo> {
    val needle = query.trim().lowercase()
    return aliases.filter { alias ->
        when {
            status == AliasStatus.Active && !alias.is_active -> false
            status == AliasStatus.Paused && alias.is_active -> false
            needle.isEmpty() -> true
            else -> alias.address.lowercase().contains(needle) ||
                alias.label.orEmpty().lowercase().contains(needle)
        }
    }
}

fun summarize(aliases: List<AliasInfo>) = AliasSummary(
    total = aliases.size,
    active = aliases.count { it.is_active },
    received = aliases.sumOf { it.emails_received },
    forwarded = aliases.sumOf { it.emails_forwarded },
)

/** The share of received mail that was forwarded, or null when nothing has arrived. */
fun forwardRate(received: Int, forwarded: Int): Float? =
    if (received <= 0) null else (forwarded.toFloat() / received).coerceIn(0f, 1f)
