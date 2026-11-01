package eu.zeletrik.devcore.tools.unicode

enum class EscapeFormat(val label: String) {
	UTF16("\\uXXXX"),
	BRACED("\\u{X}"),
	HTML("&#xX;"),
	CODE_POINTS("U+XXXX"),
}

private const val ASCII_MAX = 0x7F

object UnicodeEscapes {
	private val REPLACEMENT = Char(0xFFFD)

	fun encode(text: String, format: EscapeFormat, escapeAscii: Boolean): String {
		if (format == EscapeFormat.CODE_POINTS) {
			return text.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }
		}
		return buildString {
			text.codePoints().forEach { cp ->
				when {
					cp <= ASCII_MAX && !escapeAscii -> appendCodePoint(cp)
					format == EscapeFormat.UTF16 -> Character.toChars(cp).forEach { append("\\u%04X".format(it.code)) }
					format == EscapeFormat.BRACED -> append("\\u{%X}".format(cp))
					else -> append("&#x%X;".format(cp))
				}
			}
		}
	}

	// Possessive quantifiers: a long `U+` run must not recurse once per token (that overflowed at a few thousand).
	private val escape = Regex(
		"""\\u(?<utf16>[0-9A-Fa-f]{4})|\\u\{(?<braced>[0-9A-Fa-f]{1,6})\}|""" +
			"""&#[xX](?<hex>[0-9A-Fa-f]+);|&#(?<decimal>[0-9]+);|""" +
			"""U\+[0-9A-Fa-f]++(?: U\+[0-9A-Fa-f]++)*+""",
	)

	/** Never fails: anything that isn't a valid escape stays as written; unpaired surrogates become U+FFFD. */
	fun decode(text: String): String {
		val decoded = escape.replace(text) { match ->
			val utf16 = match.groups["utf16"]?.value
			val braced = match.groups["braced"]?.value
			val hex = match.groups["hex"]?.value
			val decimal = match.groups["decimal"]?.value
			when {
				utf16 != null -> utf16.toInt(16).toChar().toString()
				braced != null -> codePoint(braced.toInt(16)) ?: match.value
				hex != null -> hex.toIntOrNull(16)?.let(::codePoint) ?: match.value
				decimal != null -> decimal.toIntOrNull()?.let(::codePoint) ?: match.value
				else -> decodeRun(match.value)
			}
		}
		return replaceLoneSurrogates(decoded)
	}

	/** `U+0048 U+0069` → `Hi`: a run's tokens collapse into characters; a token that isn't 4–6 digits stays. */
	private fun decodeRun(run: String): String {
		val raw = run.split(' ')
		val tokens = raw.map { token ->
			val digits = token.removePrefix("U+")
			if (digits.length in 4..6) codePoint(digits.toInt(16)) else null
		}
		return buildString {
			tokens.forEachIndexed { i, char ->
				if (i > 0 && (char == null || tokens[i - 1] == null)) append(' ')
				append(char ?: raw[i])
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
