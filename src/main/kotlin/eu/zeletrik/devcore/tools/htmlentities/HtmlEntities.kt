package eu.zeletrik.devcore.tools.htmlentities

import org.jsoup.nodes.Entities

private const val ASCII_MAX = 0x7F
private const val HEX = 16

/** The C1 control range, which numeric references map through Windows-1252. */
private const val C1_FIRST = 0x80
private const val C1_LAST = 0x9F

/** Named and numeric HTML entities, encoded and decoded as browsers do. */
object HtmlEntities {
	/**
	 * The five HTML-significant characters, plus hex references for non-ASCII when asked. The apostrophe becomes
	 * `&#39;`, since HTML 4 has no `&apos;`.
	 */
	fun encode(text: String, nonAscii: Boolean): String = buildString {
		text.codePoints().forEach { cp ->
			when {
				cp == '&'.code -> append("&amp;")
				cp == '<'.code -> append("&lt;")
				cp == '>'.code -> append("&gt;")
				cp == '"'.code -> append("&quot;")
				cp == '\''.code -> append("&#39;")
				nonAscii && cp > ASCII_MAX -> append("&#x").append(cp.toString(HEX).uppercase()).append(';')
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
			else -> numeric(if (decimal.isNotEmpty()) decimal.toIntOrNull() else hex.toIntOrNull(HEX)) ?: match.value
		}
	}

	/**
	 * A numeric reference as browsers read it (HTML standard): 0 and surrogates become U+FFFD, and 0x80–0x9F use the
	 * Windows-1252 characters legacy pages meant (`&#150;` is –). Values beyond Unicode stay as written.
	 */
	private fun numeric(cp: Int?): String? = when {
		cp == null || !Character.isValidCodePoint(cp) -> null
		cp == 0 || cp in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code -> REPLACEMENT
		cp in C1_FIRST..C1_LAST -> (windows1252[cp - C1_FIRST].takeIf { it != 0 } ?: cp).toChar().toString()
		else -> Character.toString(cp)
	}

	private val REPLACEMENT = Char(0xFFFD).toString()

	/** 0x80–0x9F in Windows-1252; 0 where the HTML standard keeps the control character. */
	private val windows1252 = intArrayOf(
		0x20AC,
		0,
		0x201A,
		0x0192,
		0x201E,
		0x2026,
		0x2020,
		0x2021,
		0x02C6,
		0x2030,
		0x0160,
		0x2039,
		0x0152,
		0,
		0x017D,
		0,
		0,
		0x2018,
		0x2019,
		0x201C,
		0x201D,
		0x2022,
		0x2013,
		0x2014,
		0x02DC,
		0x2122,
		0x0161,
		0x203A,
		0x0153,
		0,
		0x017E,
		0x0178,
	)
}
