package eu.zeletrik.devcore.tools.urlparser

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** RFC 3986 parsing with java.net.URI; the host is shown, never resolved. */
object UrlParser {
	private val defaultPorts = mapOf("http" to 80, "https" to 443, "ws" to 80, "wss" to 443, "ftp" to 21)

	/** One line per part, then the query parameters decoded; the host is shown exactly as written. */
	fun describe(text: String): String {
		val uri = try {
			URI(text.trim())
		} catch (e: URISyntaxException) {
			throw IllegalArgumentException("Not a valid URL: ${e.reason}", e)
		}
		require(uri.isAbsolute) { "Not an absolute URL (it needs a scheme like https://)" }
		val authority = authority(uri)
		val port = authority.port ?: defaultPorts[uri.scheme.lowercase()]?.let { "default ($it)" }
		val lines = listOfNotNull(
			"Scheme: ${uri.scheme}",
			authority.userInfo?.let { "Username: ${percentDecoded(it.substringBefore(':'))}" },
			authority.userInfo?.takeIf { ':' in it }?.let { "Password: ${percentDecoded(it.substringAfter(':'))}" },
			authority.host?.let { "Host: $it" },
			port?.let { "Port: $it" },
			uri.rawPath?.takeIf { it.isNotEmpty() }?.let { "Path: $it" },
			uri.rawQuery?.let { "Query: $it" },
			uri.rawFragment?.let { "Fragment: $it" },
		)
		val parameters = uri.rawQuery?.split('&')?.filter { it.isNotEmpty() }?.map { pair ->
			"  ${decode(pair.substringBefore('='))} = ${decode(pair.substringAfter('=', ""))}".trimEnd()
		}.orEmpty()
		val query = if (parameters.isEmpty()) emptyList() else listOf("Query parameters:") + parameters
		return (lines + query).joinToString("\n")
	}

	/** Query percent-decoding, `+` as a space; java.net.URI already rejected any malformed escape. */
	private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)

	private class Authority(val userInfo: String?, val host: String?, val port: String?)

	/**
	 * Raw userinfo, host and written port. `java.net.URI` treats hosts it can't parse as a server name (`_`,
	 * non-ASCII) as a "registry" authority and returns no host, port or userinfo, so those are split by hand.
	 */
	private fun authority(uri: URI): Authority {
		val raw = uri.rawAuthority
		return when {
			uri.host != null -> Authority(uri.rawUserInfo, uri.host, uri.port.takeIf { it >= 0 }?.toString())
			raw == null -> Authority(null, null, null)
			else -> registryAuthority(raw)
		}
	}

	private fun registryAuthority(raw: String): Authority {
		val userInfo = if ('@' in raw) raw.substringBeforeLast('@') else null
		val hostPort = raw.substringAfterLast('@')
		val portText = hostPort.substringAfterLast(':', "")
		val hasPort = ':' in hostPort && portText.all { it in '0'..'9' }
		val host = if (hasPort) hostPort.substringBeforeLast(':') else hostPort
		return Authority(userInfo, host.ifEmpty { null }, portText.takeIf { hasPort && it.isNotEmpty() })
	}

	/** Userinfo percent-decoding, where `+` is a plain character. */
	private fun percentDecoded(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
}
