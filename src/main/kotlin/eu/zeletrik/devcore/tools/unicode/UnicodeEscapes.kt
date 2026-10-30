package eu.zeletrik.devcore.tools.unicode

enum class EscapeFormat(val label: String) {
	UTF16("\\uXXXX"),
	BRACED("\\u{X}"),
	HTML("&#xX;"),
	CODE_POINTS("U+XXXX"),
}

object UnicodeEscapes {
	private val REPLACEMENT = Char(0xFFFD)

	fun encode(text: String, format: EscapeFormat, escapeAscii: Boolean): String {
		if (format == EscapeFormat.CODE_POINTS) {
			return text.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }
		}
		return buildString {
			text.codePoints().forEach { cp ->
				when {
					cp <= 0x7F && !escapeAscii -> appendCodePoint(cp)
					format == EscapeFormat.UTF16 -> Character.toChars(cp).forEach { append("\\u%04X".format(it.code)) }
					format == EscapeFormat.BRACED -> append("\\u{%X}".format(cp))
					else -> append("&#x%X;".format(cp))
				}
			}
		}
	}

	private val escape = Regex(
		"""\\u([0-9A-Fa-f]{4})|\\u\{([0-9A-Fa-f]{1,6})\}|&#[xX]([0-9A-Fa-f]+);|&#([0-9]+);|(U\+[0-9A-Fa-f]+(?: U\+[0-9A-Fa-f]+)*)""",
	)

	/** Never fails: anything that isn't a valid escape stays as written; unpaired surrogates become U+FFFD. */
	fun decode(text: String): String {
		val decoded = escape.replace(text) { match ->
			val (utf16, braced, hex, decimal, run) = match.destructured
			when {
				utf16.isNotEmpty() -> utf16.toInt(16).toChar().toString()
				braced.isNotEmpty() -> codePoint(braced.toInt(16)) ?: match.value
				hex.isNotEmpty() -> hex.toIntOrNull(16)?.let(::codePoint) ?: match.value
				decimal.isNotEmpty() -> decimal.toIntOrNull()?.let(::codePoint) ?: match.value
				else -> decodeRun(run)
			}
		}
		return replaceLoneSurrogates(decoded)
	}

	/** `U+0048 U+0069` → `Hi`: a run's tokens collapse into characters; a token that isn't 4–6 digits stays. */
	private fun decodeRun(run: String): String {
		val tokens = run.split(' ').map { token ->
			val digits = token.removePrefix("U+")
			if (digits.length in 4..6) codePoint(digits.toInt(16)) else null
		}
		return buildString {
			tokens.forEachIndexed { i, char ->
				if (i > 0 && (char == null || tokens[i - 1] == null)) append(' ')
				append(char ?: run.split(' ')[i])
			}
		}
	}

	private fun codePoint(cp: Int) = cp.takeIf { it <= Character.MAX_CODE_POINT }?.let { String(Character.toChars(it)) }

	private fun replaceLoneSurrogates(text: String) = buildString {
		var i = 0
		while (i < text.length) {
			val c = text[i]
			when {
				c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
					append(c).append(text[i + 1])
					i++
				}
				c.isSurrogate() -> append(REPLACEMENT)
				else -> append(c)
			}
			i++
		}
	}
}
