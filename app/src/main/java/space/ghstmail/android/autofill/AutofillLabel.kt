package space.ghstmail.android.autofill

private const val MAX_LABEL = 40

private val domainParts = setOf(
    "com", "org", "net", "in", "io", "co", "app", "dev", "me", "uk", "us", "de", "fr", "jp", "ai", "xyz"
)
private val genericParts = setOf(
    "android", "app", "apps", "mobile", "client", "lite", "staging", "debug", "beta", "release", "prod"
)

/** What to call an alias made from an email field somewhere else: the website, else the app. */
fun aliasLabel(webDomain: String?, appName: String?, packageName: String): String {
    val site = webDomain?.trim()?.lowercase()?.removePrefix("www.").orEmpty()
    if (site.isNotEmpty()) return site.take(MAX_LABEL)
    val name = appName?.trim().orEmpty()
    if (name.isNotEmpty() && name != packageName) return name.take(MAX_LABEL)
    return nameFromPackage(packageName).take(MAX_LABEL)
}

/** "com.pinterest" becomes "Pinterest", for when the system will not say what the app is called. */
internal fun nameFromPackage(packageName: String): String {
    val parts = packageName.lowercase().split('.').filter { it.isNotEmpty() }
    val named = parts.dropWhile { it in domainParts }.firstOrNull { it !in genericParts }
        ?: parts.lastOrNull()
        ?: return ""
    return named.replaceFirstChar { it.uppercase() }
}
