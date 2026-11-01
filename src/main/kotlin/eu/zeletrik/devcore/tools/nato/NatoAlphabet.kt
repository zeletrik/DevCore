package eu.zeletrik.devcore.tools.nato

/** ICAO/NATO spelling: one token per character, `/` for a run of whitespace, anything else copied as is. */
object NatoAlphabet {
	private val words = listOf(
		"Alfa", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliett", "Kilo", "Lima", "Mike",
		"November", "Oscar", "Papa", "Quebec", "Romeo", "Sierra", "Tango", "Uniform", "Victor", "Whiskey", "X-ray",
		"Yankee", "Zulu",
	)
	private val digits = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")

	// One token per extended grapheme cluster: a flag or a letter with its accent is one character to a reader.
	fun spell(text: String): String = Regex("\\s+|\\X").findAll(text.trim()).joinToString(" ") { match ->
		val token = match.value
		val c = token[0]
		when {
			c.isWhitespace() -> "/"
			// Only a lone ASCII letter or digit has a code word; an accented letter stays as written, composed or not.
			token.length == 1 && c.uppercaseChar() in 'A'..'Z' -> words[c.uppercaseChar() - 'A']
			token.length == 1 && c in '0'..'9' -> digits[c - '0']
			else -> token
		}
	}
}
