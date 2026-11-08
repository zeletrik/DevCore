package eu.zeletrik.devcore.tools.base64

import java.util.Base64

/** Text as Base64 and back, in the standard or the URL-safe alphabet. */
object Base64Codec {
	/** Standard Base64 is padded; the URL-safe variant drops the padding, as JWTs and most URL uses expect. */
	fun encode(text: String, urlSafe: Boolean): String {
		val encoder = if (urlSafe) Base64.getUrlEncoder().withoutPadding() else Base64.getEncoder()
		return encoder.encodeToString(text.toByteArray(Charsets.UTF_8))
	}

	/** Whitespace is ignored and padding is optional; invalid UTF-8 comes back as U+FFFD. */
	fun decode(text: String, urlSafe: Boolean): String {
		val decoder = if (urlSafe) Base64.getUrlDecoder() else Base64.getDecoder()
		val bytes = try {
			decoder.decode(text.filterNot(Char::isWhitespace))
		} catch (e: IllegalArgumentException) {
			throw IllegalArgumentException("Invalid Base64", e)
		}
		return String(bytes, Charsets.UTF_8)
	}
}
