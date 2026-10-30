package eu.zeletrik.devcore.tools.casing

enum class Case(val label: String) {
	CAMEL("camelCase"), PASCAL("PascalCase"), SNAKE("snake_case"), KEBAB("kebab-case"), CONSTANT("CONSTANT_CASE"),
	DOT("dot.case"), TITLE("Title Case"), SENTENCE("Sentence case"), LOWER("lower case"), UPPER("UPPER CASE"),
}

/** Case mapping is locale-independent: Kotlin's lowercase()/uppercase() use the root locale. */
object CaseConverter {
	// Lower-case letter or digit → upper-case letter, and before the last capital of an acronym (HTTPServer).
	private val boundary = Regex("(?<=[\\p{Ll}\\p{N}])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})")
	private val separator = Regex("[^\\p{L}\\p{N}]+")

	fun words(line: String): List<String> =
		line.split(separator).filter { it.isNotEmpty() }.flatMap { it.split(boundary) }.map { it.lowercase() }

	fun convert(text: String, case: Case): String = text.split("\n").joinToString("\n") { line -> convertLine(line, case) }

	private fun convertLine(line: String, case: Case): String {
		val words = words(line)
		fun capital(word: String) = word.replaceFirstChar { it.uppercase() }
		return when (case) {
			Case.CAMEL -> words.mapIndexed { i, w -> if (i == 0) w else capital(w) }.joinToString("")
			Case.PASCAL -> words.joinToString("") { capital(it) }
			Case.SNAKE -> words.joinToString("_")
			Case.KEBAB -> words.joinToString("-")
			Case.CONSTANT -> words.joinToString("_") { it.uppercase() }
			Case.DOT -> words.joinToString(".")
			Case.TITLE -> words.joinToString(" ") { capital(it) }
			Case.SENTENCE -> words.mapIndexed { i, w -> if (i == 0) capital(w) else w }.joinToString(" ")
			Case.LOWER -> line.lowercase()
			Case.UPPER -> line.uppercase()
		}
	}
}
