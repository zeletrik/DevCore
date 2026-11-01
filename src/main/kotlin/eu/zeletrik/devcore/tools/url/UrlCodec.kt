package eu.zeletrik.devcore.tools.url

import org.springframework.web.util.UriUtils
import java.net.URLDecoder

object UrlCodec {
	/** Leaves exactly the RFC 3986 unreserved set (A–Z a–z 0–9 - . _ ~) unencoded. */
	fun encode(text: String): String = UriUtils.encode(text, Charsets.UTF_8)

	/** `+` becomes a space; malformed UTF-8 comes back as U+FFFD. */
	fun decode(text: String): String = try {
		URLDecoder.decode(text, Charsets.UTF_8)
	} catch (e: IllegalArgumentException) {
		throw IllegalArgumentException("Malformed percent-encoding", e)
	}
}
