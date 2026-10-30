package eu.zeletrik.devcore.tools.urlparser

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** RFC 3986 parsing with java.net.URI; the host is shown, never resolved. */
object UrlParser {
	private val defaultPorts = mapOf("http" to 80, "https" to 443, "ws" to 80, "wss" to 443, "ftp" to 21)

	fun describe(text: String): String {
		val uri = try {
			URI(text.trim())
		} catch (e: URISyntaxException) {
			throw IllegalArgumentException("Not a valid URL: ${e.reason}")
		}
		require(uri.isAbsolute) { "Not an absolute URL (it needs a scheme like https://)" }
		val user = uri.userInfo
		val port = when {
			uri.port >= 0 -> uri.port.toString()
			defaultPorts[uri.scheme.lowercase()] != null -> "default (${defaultPorts[uri.scheme.lowercase()]})"
			else -> null
		}
		val lines = listOfNotNull(
			"Scheme: ${uri.scheme}",
			user?.let { "Username: ${it.substringBefore(':')}" },
			user?.takeIf { ':' in it }?.let { "Password: ${it.substringAfter(':')}" },
			uri.host?.let { "Host: $it" },
			port?.let { "Port: $it" },
			uri.rawPath?.takeIf { it.isNotEmpty() }?.let { "Path: $it" },
			uri.rawQuery?.let { "Query: $it" },
			uri.rawFragment?.let { "Fragment: $it" },
		)
		val parameters = uri.rawQuery?.split('&')?.filter { it.isNotEmpty() }?.map { pair ->
			"  ${decode(pair.substringBefore('='))} = ${decode(pair.substringAfter('=', ""))}".trimEnd()
		}.orEmpty()
		return (lines + if (parameters.isEmpty()) emptyList() else listOf("Query parameters:") + parameters).joinToString("\n")
	}

	/** Percent-decoding with `+` as a space; a malformed escape is shown as written. */
	private fun decode(value: String) = runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)
}
