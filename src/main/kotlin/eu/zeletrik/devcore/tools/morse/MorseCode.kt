package eu.zeletrik.devcore.tools.morse

/** ITU-R M.1677-1 letters, digits and punctuation. */
object MorseCode {
	private val codes = mapOf(
		'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....",
		'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.",
		'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
		'Y' to "-.--", 'Z' to "--..",
		'0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....",
		'7' to "--...", '8' to "---..", '9' to "----.",
		'.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.", '!' to "-.-.--", '/' to "-..-.",
		'(' to "-.--.", ')' to "-.--.-", '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-", '+' to ".-.-.",
		'-' to "-....-", '_' to "..--.-", '"' to ".-..-.", '$' to "...-..-", '@' to ".--.-.",
	)
	private val letters = codes.entries.associate { (char, code) -> code to char }
	/** Any Unicode space separates, the no-break space included (`\\s` alone is ASCII-only). */
	private val separators = Regex("[\\s\\p{Z}]+")

	fun encode(text: String): String =
		text.trim().split(separators).filter { it.isNotEmpty() }.joinToString(" / ") { word ->
		word.codePoints().toArray().joinToString(" ") { cp ->
			val char = String(Character.toChars(cp))
			char.singleOrNull()?.let { codes[it.uppercaseChar()] } ?: throw IllegalArgumentException("No Morse code for '$char'")
		}
	}

	fun decode(text: String): String {
		require(text.all { it == '.' || it == '-' || it == '/' || it.isWhitespace() }) {
			"Morse code may contain only '.', '-', '/' and spaces"
		}
		return text.split('/').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") { word ->
			word.split(separators).filter { it.isNotEmpty() }.joinToString("") { code ->
				letters[code]?.toString() ?: throw IllegalArgumentException("Unknown Morse code '$code'")
			}
		}
	}
}
