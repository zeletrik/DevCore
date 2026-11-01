package eu.zeletrik.devcore.tools.urlparser

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.util.HexFormat

private const val HEX = 16

/** A percent escape: `%` and two hex digits. */
private const val ESCAPE_LENGTH = 3

/** RFC 3986 parsing with java.net.URI; the host is shown, never resolved. */
object UrlParser {
	private val defaultPorts = mapOf("http" to 80, "https" to 443, "ws" to 80, "wss" to 443, "ftp" to 21)

	fun describe(text: String): String {
		val uri = try {
			URI(text.trim())
		} catch (e: URISyntaxException) {
			throw IllegalArgumentException("Not a valid URL: ${e.reason}", e)
		}
		require(uri.isAbsolute) { "Not an absolute URL (it needs a scheme like https://)" }
		val authority = authority(uri)
		val port = when {
			authority.port != null -> authority.port
			defaultPorts[uri.scheme.lowercase()] != null -> "default (${defaultPorts[uri.scheme.lowercase()]})"
			else -> null
		}
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

	/** Percent-decoding with `+` as a space; a malformed escape is shown as written. */
	private fun decode(value: String) = runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)

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

	/** Percent-decoding as UTF-8 that leaves `+` alone (it only means a space in query strings). */
	private fun percentDecoded(value: String): String {
		val bytes = ByteArrayOutputStream()
		var i = 0
		while (i < value.length) {
			val hex = value.substring(i + 1, minOf(i + ESCAPE_LENGTH, value.length))
			if (value[i] == '%' && hex.length == 2 && hex.all { HexFormat.isHexDigit(it.code) }) {
				bytes.write(hex.toInt(HEX))
				i += ESCAPE_LENGTH
			} else {
				bytes.write(value[i].toString().toByteArray(Charsets.UTF_8))
				i++
			}
		}
		return bytes.toString(Charsets.UTF_8)
	}
}
