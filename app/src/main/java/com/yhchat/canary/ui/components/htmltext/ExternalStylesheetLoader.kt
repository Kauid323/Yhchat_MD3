package com.yhchat.canary.ui.components.htmltext

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.SocketTimeoutException

private const val MAX_STYLESHEET_COUNT = 3
private const val MAX_STYLESHEET_BYTES = 96 * 1024
private const val MAX_CSS_LINK_SCAN_LENGTH = 60_000
private const val MAX_REDIRECTS = 3
private const val CONNECT_TIMEOUT_MS = 4_000
private const val READ_TIMEOUT_MS = 4_000
private const val MAX_FETCH_DURATION_MS = 12_000L

/**
 * Fetches a small, bounded set of remote stylesheets referenced by HTML.
 *
 * The map key intentionally remains the href as written in the HTML. The
 * parser uses that key while walking the document, so stylesheet order stays
 * the same as the order of the corresponding <link> and <style> elements.
 */
internal suspend fun loadExternalStylesheets(html: String, baseUrl: String?): Map<String, String> =
    withContext(Dispatchers.IO) {
        ensureActiveIo()
        val links = findExternalStylesheetLinks(html, baseUrl)
        if (links.isEmpty()) return@withContext emptyMap()

        coroutineScope {
            links.map { link ->
                async {
                    fetchStylesheet(link.resolvedUrl)?.let { css -> link.rawHref to css }
                }
            }.awaitAll()
                .filterNotNull()
                .toMap(LinkedHashMap())
        }
    }

private data class StylesheetLink(
    val rawHref: String,
    val resolvedUrl: String
)

private suspend fun fetchStylesheet(initialUrl: String): String? = withContext(Dispatchers.IO) {
    val startedAt = System.nanoTime()
    var currentUrl = initialUrl
    var redirects = 0
    while (true) {
        ensureActiveIo()
        val connection = runCatching {
            URL(currentUrl).openConnection() as? HttpURLConnection
        }.getOrNull() ?: return@withContext null
        try {
            connection.connectTimeout = remainingTimeout(startedAt, CONNECT_TIMEOUT_MS)
            connection.readTimeout = remainingTimeout(startedAt, READ_TIMEOUT_MS)
            // Follow redirects ourselves to keep the redirect chain bounded and
            // to reject a redirect to a non-http(s) or credential-bearing URL.
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/css,*/*;q=0.1")
            connection.setRequestProperty("User-Agent", "Yhchat-HTML-Renderer/1.0")
            connection.connect()
            ensureActiveIo()
            connection.readTimeout = remainingTimeout(startedAt, READ_TIMEOUT_MS)

            when (connection.responseCode) {
                in 200..299 -> {
                    val advertisedLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    if (advertisedLength != null && advertisedLength > MAX_STYLESHEET_BYTES) return@withContext null
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    connection.inputStream.use { input ->
                        while (true) {
                            ensureActiveIo()
                            connection.readTimeout = remainingTimeout(startedAt, READ_TIMEOUT_MS)
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (bytes.size() + count > MAX_STYLESHEET_BYTES) return@withContext null
                            bytes.write(buffer, 0, count)
                        }
                    }
                    ensureActiveIo()
                    remainingTimeout(startedAt, READ_TIMEOUT_MS)
                    return@withContext bytes.toByteArray().toString(Charsets.UTF_8)
                }

                301, 302, 303, 307, 308 -> {
                    if (redirects++ >= MAX_REDIRECTS) return@withContext null
                    val location = connection.getHeaderField("Location") ?: return@withContext null
                    currentUrl = resolveHttpUrl(location, currentUrl) ?: return@withContext null
                }

                else -> {
                    // Consume no error body: this keeps failure cheap and bounded.
                    return@withContext null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ensureActiveIo()
            return@withContext null
        } finally {
            connection.disconnect()
        }
    }
    @Suppress("UNREACHABLE_CODE")
    null
}

/** A shared deadline prevents redirects or a slowly trickling response from extending the budget. */
private fun remainingTimeout(startedAt: Long, maximumMillis: Int): Int {
    val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
    val remainingMillis = MAX_FETCH_DURATION_MS - elapsedMillis
    if (remainingMillis <= 0) throw SocketTimeoutException("Stylesheet fetch deadline exceeded")
    return minOf(maximumMillis.toLong(), remainingMillis).toInt().coerceAtLeast(1)
}

/** A cancellation check that does not turn coroutine cancellation into a fetch failure. */
private suspend fun ensureActiveIo() {
    currentCoroutineContext().ensureActive()
}

/** Extracts stylesheet links and resolves them against the first <base> or [baseUrl]. */
private fun findExternalStylesheetLinks(html: String, baseUrl: String?): List<StylesheetLink> {
    if (html.isBlank() || html.length > MAX_CSS_LINK_SCAN_LENGTH) return emptyList()

    val source = runCatching { parseNativeHtmlDocument(html) }.getOrElse { return emptyList() }
    val elements = source.activeElements.filter { it.tag().namespace() == org.jsoup.parser.Parser.NamespaceHtml }
    val documentBase = elements.firstOrNull { it.normalName() == "base" && it.hasAttr("href") }
        ?.attr("href")
    val hrefs = elements.asSequence()
        .filter { it.normalName() == "link" && isStylesheetLink(it.htmlAttributes()) }
        .map { it.attr("href") }
        .filter { it.isNotBlank() }

    val base = documentBase?.let { resolveHttpUrl(it, baseUrl) }
        ?: baseUrl?.let { resolveHttpUrl(it, null) }
    return hrefs.asSequence()
        .mapNotNull { rawHref ->
            resolveHttpUrl(rawHref, base)?.let { StylesheetLink(rawHref, it) }
        }
        .distinctBy { it.rawHref }
        .take(MAX_STYLESHEET_COUNT)
        .toList()
}

private fun resolveHttpUrl(reference: String, base: String?): String? {
    val ref = runCatching { URI(reference.trim()) }.getOrNull() ?: return null
    val resolved = runCatching {
        if (ref.isAbsolute) ref else base?.let { URI(it).resolve(ref) }
    }.getOrNull() ?: return null
    val scheme = resolved.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (resolved.userInfo != null || resolved.host.isNullOrBlank()) return null
    return runCatching { resolved.normalize().toURL().toExternalForm() }.getOrNull()
}
