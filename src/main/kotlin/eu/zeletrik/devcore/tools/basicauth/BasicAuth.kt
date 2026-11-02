package eu.zeletrik.devcore.tools.basicauth

import eu.zeletrik.devcore.tools.FieldError
import java.util.Base64

/** RFC 7617: the UTF-8 bytes of `user:password`, Base64-encoded. */
object BasicAuth {
	private val prefix = Regex("^((proxy-)?authorization:\\s*)?(basic\\s+)?", RegexOption.IGNORE_CASE)

	fun encode(username: String, password: String): String {
		if (':' in username) throw FieldError("username", "A username can't contain ':'")
		return "Authorization: Basic " + Base64.getEncoder()
			.encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
	}

	/** Accepts the full header, `Basic X` or just `X`; the password is everything after the first colon. */
	fun decode(text: String): Pair<String, String> {
		val token = text.trim().replace(prefix, "").trim()
		val decoded = try {
			String(Base64.getDecoder().decode(token), Charsets.UTF_8)
		} catch (_: IllegalArgumentException) {
			throw IllegalArgumentException("Invalid Base64")
		}
		val colon = decoded.indexOf(':')
		require(colon >= 0) { "Decoded value has no ':' between username and password" }
		return decoded.substring(0, colon) to decoded.substring(colon + 1)
	}
}
