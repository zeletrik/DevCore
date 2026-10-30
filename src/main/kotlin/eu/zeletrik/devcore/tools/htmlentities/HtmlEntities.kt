package eu.zeletrik.devcore.tools.htmlentities

import org.jsoup.nodes.Entities

object HtmlEntities {
	fun encode(text: String, nonAscii: Boolean): String = buildString {
		text.codePoints().forEach { cp ->
			when {
				cp == '&'.code -> append("&amp;")
				cp == '<'.code -> append("&lt;")
				cp == '>'.code -> append("&gt;")
				cp == '"'.code -> append("&quot;")
				cp == '\''.code -> append("&#39;")
				nonAscii && cp > 0x7F -> append("&#x").append(cp.toString(16).uppercase()).append(';')
				else -> appendCodePoint(cp)
			}
		}
	}

	// Strict on purpose: only `;`-terminated references decode, so `&amp` or `& b` stay as written.
	private val reference = Regex("&(?:#([0-9]+)|#[xX]([0-9A-Fa-f]+)|([A-Za-z][A-Za-z0-9]*));")

	fun decode(text: String): String = reference.replace(text) { match ->
		val (decimal, hex, name) = match.destructured
		when {
			name.isNotEmpty() -> if (Entities.isNamedEntity(name)) Entities.getByName(name) else match.value
			else -> character(if (decimal.isNotEmpty()) decimal.toIntOrNull() else hex.toIntOrNull(16)) ?: match.value
		}
	}

	private fun character(cp: Int?) =
		cp?.takeIf { Character.isValidCodePoint(it) && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) }
}
