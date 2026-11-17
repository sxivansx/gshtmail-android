package space.ghstmail.android.ui.filters

/**
 * The domain meant by what was typed. A pasted sender address counts as its domain,
 * so " News@Spam.com " becomes "spam.com".
 */
fun normalizeDomain(input: String): String =
    input.trim().lowercase().substringAfterLast('@').trim('.', ' ')

/** Loose on purpose: the server has the final say, this only catches a slip of the thumb. */
fun looksLikeDomain(domain: String): Boolean =
    domain.contains('.') &&
        domain.none { it.isWhitespace() || it == '/' || it == ':' } &&
        domain.split('.').all { it.isNotEmpty() }
