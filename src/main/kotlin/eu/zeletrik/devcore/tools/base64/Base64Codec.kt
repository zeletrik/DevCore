package eu.zeletrik.devcore.tools.base64

import java.util.Base64

object Base64Codec {
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
			throw IllegalArgumentException("Not valid Base64")
		}
		return String(bytes, Charsets.UTF_8)
	}
}
