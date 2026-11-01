package eu.zeletrik.devcore.tools.stringescape

import java.util.HexFormat

/** Which language's string literal rules apply. */
enum class Flavor(val label: String) { JSON("JSON"), JAVA("Java"), JAVASCRIPT("JavaScript") }

/**
 * Shared escapes plus per-flavor ones: JSON `\/`; Java `\'`, `\s` and octal; JavaScript `\'`, `\v`, `\xHH`, `\u{…}`,
 * `\0` and line continuations.
 */
object StringEscapes {
	private const val HEX = 16
	private const val OCTAL = 8

	/** `\uXXXX` takes exactly four hex digits. */
	private const val UNICODE_DIGITS = 4
	private val FORM_FEED = Char(12)
	private val VERTICAL_TAB = Char(11)
	private val NUL = Char(0)
	private val LINE_SEPARATOR = Char(0x2028)
	private val PARAGRAPH_SEPARATOR = Char(0x2029)
	private val lineTerminators = setOf('\n', LINE_SEPARATOR, PARAGRAPH_SEPARATOR)

	/** The escapes every flavor shares, by the character after the backslash. */
	private val common = mapOf(
		'\\' to '\\', '"' to '"', 'n' to '\n', 'r' to '\r', 't' to '\t', 'b' to '\b', 'f' to FORM_FEED,
	)

	/** The input as the content of a string literal in [flavor], without the quotes. Non-ASCII stays as is. */
	fun escape(text: String, flavor: Flavor): String = buildString {
		text.forEach { c ->
			when {
				c == '\\' -> append("\\\\")
				c == '"' -> append("\\\"")
				c == '\'' && flavor == Flavor.JAVASCRIPT -> append("\\'")
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

	/** Applies [flavor]'s escapes; any other escape fails with the backslash's 1-based UTF-16 position. */
	fun unescape(text: String, flavor: Flavor): String = Unescaper(text, flavor).run()

	/** Reads [text] left to right; [position] is the index of the next unread character. */
	private class Unescaper(private val text: String, private val flavor: Flavor) {
		private val out = StringBuilder()
		private var position = 0
		private var backslashAt = 0

		fun run(): String {
			while (position < text.length) {
				val c = text[position++]
				if (c == '\\') escape() else out.append(c)
			}
			return out.toString()
		}

		private fun escape() {
			backslashAt = position - 1
			val next = text.getOrNull(position++) ?: invalid()
			val simple = common[next] ?: flavorOnly(next)
			when {
				simple != null -> out.append(simple)
				next == 'u' -> if (flavor == Flavor.JAVASCRIPT && text.getOrNull(position) == '{') braced() else fourDigits()
				next == 'x' -> hexByte()
				next in '0'..'7' -> octal(next)
				next == '\r' || next in lineTerminators -> lineContinuation(next)
				else -> invalid()
			}
		}

		private fun flavorOnly(next: Char): Char? = when {
			next == '/' && flavor == Flavor.JSON -> '/'
			next == '\'' && flavor != Flavor.JSON -> '\''
			next == 's' && flavor == Flavor.JAVA -> ' '
			next == 'v' && flavor == Flavor.JAVASCRIPT -> VERTICAL_TAB
			else -> null
		}

		/** JavaScript's `\u{…}`: one to six hex digits, up to U+10FFFF. */
		private fun braced() {
			val end = text.indexOf('}', position)
			val digits = if (end > position + 1) text.substring(position + 1, end) else ""
			val cp = digits.takeIf { it.isNotEmpty() && it.all(::isHex) }?.toIntOrNull(HEX)
			if (cp == null || cp > Character.MAX_CODE_POINT) invalid()
			out.appendCodePoint(cp)
			position = end + 1
		}

		// NOTE: digits are checked first because toInt accepts a leading sign, which no escape does.
		private fun fourDigits() {
			val hex = text.substring(position, minOf(position + UNICODE_DIGITS, text.length))
			if (hex.length < UNICODE_DIGITS || !hex.all(::isHex)) invalid()
			out.append(hex.toInt(HEX).toChar())
			position += UNICODE_DIGITS
		}

		private fun hexByte() {
			val hex = text.substring(position, minOf(position + 2, text.length))
			if (flavor != Flavor.JAVASCRIPT || hex.length < 2 || !hex.all(::isHex)) invalid()
			out.append(hex.toInt(HEX).toChar())
			position += 2
		}

		private fun octal(first: Char) {
			when (flavor) {
				Flavor.JAVA -> {
					// Octal escapes: up to three digits, at most \377.
					val more = if (first <= '3') 2 else 1
					var end = position
					while (end < text.length && end - position < more && text[end] in '0'..'7') end++
					out.append((first + text.substring(position, end)).toInt(OCTAL).toChar())
					position = end
				}
				Flavor.JAVASCRIPT -> if (first == '0' && text.getOrNull(position) !in '0'..'9') out.append(NUL) else invalid()
				Flavor.JSON -> invalid()
			}
		}

		/** JavaScript line continuation: a backslash before a line terminator adds nothing to the string. */
		private fun lineContinuation(terminator: Char) {
			if (flavor != Flavor.JAVASCRIPT) invalid()
			if (terminator == '\r' && text.getOrNull(position) == '\n') position++
		}

		private fun invalid(): Nothing =
			throw IllegalArgumentException("Invalid escape sequence at position ${backslashAt + 1}")
	}

	private fun isHex(c: Char) = HexFormat.isHexDigit(c.code)
}
