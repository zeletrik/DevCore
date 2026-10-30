package eu.zeletrik.devcore.tools.stringescape

enum class Flavour(val label: String) { JSON("JSON"), JAVA("Java"), JAVASCRIPT("JavaScript") }

object StringEscapes {
	private val FORM_FEED = Char(12)
	private val VERTICAL_TAB = Char(11)
	private val NUL = Char(0)

	/** The input as the content of a string literal in [flavour], without the quotes. Non-ASCII stays as is. */
	fun escape(text: String, flavour: Flavour): String = buildString {
		text.forEach { c ->
			when {
				c == '\\' -> append("\\\\")
				c == '"' -> append("\\\"")
				c == '\'' && flavour == Flavour.JAVASCRIPT -> append("\\'")
				c == '\n' -> append("\\n")
				c == '\r' -> append("\\r")
				c == '\t' -> append("\\t")
				c == '\b' -> append("\\b")
				c == FORM_FEED -> append("\\f")
				c < ' ' -> append("\\u%04X".format(c.code))
				else -> append(c)
			}
		}
	}

	/** Applies [flavour]'s escapes; any other escape fails with the backslash's 1-based UTF-16 position. */
	fun unescape(text: String, flavour: Flavour): String = buildString {
		var i = 0
		while (i < text.length) {
			val c = text[i]
			if (c != '\\') {
				append(c)
				i++
				continue
			}
			val at = i
			fun invalid(): Nothing = throw IllegalArgumentException("Invalid escape sequence at position ${at + 1}")
			val next = text.getOrNull(i + 1) ?: invalid()
			i += 2
			when (next) {
				'\\' -> append('\\')
				'"' -> append('"')
				'n' -> append('\n')
				'r' -> append('\r')
				't' -> append('\t')
				'b' -> append('\b')
				'f' -> append(FORM_FEED)
				'u' -> {
					if (flavour == Flavour.JAVASCRIPT && text.getOrNull(i) == '{') {
						val end = text.indexOf('}', i)
						val cp = text.substring(i + 1, maxOf(end, i + 1)).takeIf { end > i + 1 && it.all(::isHex) }?.toIntOrNull(16)
						if (cp == null || cp > Character.MAX_CODE_POINT) invalid()
						appendCodePoint(cp)
						i = end + 1
					} else {
						val hex = text.substring(i, minOf(i + 4, text.length))
						if (hex.length < 4 || !hex.all(::isHex)) invalid()
						append(hex.toInt(16).toChar())
						i += 4
					}
				}
				'/' -> if (flavour == Flavour.JSON) append('/') else invalid()
				'\'' -> if (flavour != Flavour.JSON) append('\'') else invalid()
				's' -> if (flavour == Flavour.JAVA) append(' ') else invalid()
				'v' -> if (flavour == Flavour.JAVASCRIPT) append(VERTICAL_TAB) else invalid()
				'x' -> {
					val hex = text.substring(i, minOf(i + 2, text.length))
					if (flavour != Flavour.JAVASCRIPT || hex.length < 2 || !hex.all(::isHex)) invalid()
					append(hex.toInt(16).toChar())
					i += 2
				}
				in '0'..'7' -> when (flavour) {
					Flavour.JAVA -> {
						// Octal escapes: up to three digits, at most \377.
						var end = i
						while (end < text.length && end - i < (if (next <= '3') 2 else 1) && text[end] in '0'..'7') end++
						append((next + text.substring(i, end)).toInt(8).toChar())
						i = end
					}
					Flavour.JAVASCRIPT -> if (next == '0' && text.getOrNull(i) !in '0'..'9') append(NUL) else invalid()
					Flavour.JSON -> invalid()
				}
				else -> invalid()
			}
		}
	}

	private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
