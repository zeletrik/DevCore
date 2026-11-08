package eu.zeletrik.devcore.tools.dockercompose

private const val PWD = "$(pwd)"
private const val SUBSTITUTION = "Command substitution isn't supported, except $(pwd)"
private const val REDIRECT = "Pipes, redirects and background jobs aren't supported"

/** Characters a shell word can hold without quotes. */
private val safe = Regex("[A-Za-z0-9_./:@%+=,~-]+")

/**
 * Reads shell text as a POSIX shell splits it into commands and words, without running anything: quotes, backslashes,
 * comments and line continuations. Newlines, `;`, `&&` and `||` end a command. `$VAR` and `${VAR}` are kept, since
 * Compose reads them the same way. A `$` the shell would keep as text becomes `$$`, Compose's escape for it, and
 * `$(pwd)` becomes `${PWD}`.
 */
internal class ShellReader(text: String) {
	private val text = text.replace("\r\n", "\n")
	private val variables = Variables(this.text)
	private val commands = mutableListOf<List<String>>()
	private var words = mutableListOf<String>()
	private val word = StringBuilder()
	private var inWord = false
	private var i = 0

	fun commands(): List<List<String>> {
		while (i < text.length) step(text[i])
		endCommand()
		return commands
	}

	private fun step(c: Char) {
		when {
			c == '\\' -> escaped()
			c == '\'' -> singleQuoted()
			c == '"' -> doubleQuoted()
			c == '$' -> dollar()
			c == '`' -> throw IllegalArgumentException(SUBSTITUTION)
			c == '#' && !inWord -> i = text.indexOf('\n', i).takeIf { it >= 0 } ?: text.length
			c in "&|" -> operator(c)
			c in "<>" -> throw IllegalArgumentException(REDIRECT)
			c in " \t\r" -> {
				endWord()
				i++
			}

			c in "\n;" -> {
				endCommand()
				i++
			}

			else -> {
				literal(c)
				i++
			}
		}
	}

	/** A backslash outside quotes keeps the next character as text; before a newline, it continues the line. */
	private fun escaped() {
		val next = text.getOrNull(i + 1)
		if (next != '\n') literal(next ?: '\\')
		i += 2
	}

	private fun singleQuoted() {
		val end = text.indexOf('\'', i + 1)
		require(end >= 0) { "Unclosed ' quote" }
		inWord = true
		for (j in i + 1 until end) literal(text[j])
		i = end + 1
	}

	/** Inside double quotes, a backslash only escapes `"`, `\`, `$`, a backtick and a newline. */
	private fun doubleQuoted() {
		inWord = true
		i++
		while (text.getOrNull(i) != '"') {
			require(i < text.length) { "Unclosed \" quote" }
			val c = text[i]
			val next = text.getOrNull(i + 1)
			when {
				c == '\\' && next != null && next in "\"\\$`\n" -> {
					if (next != '\n') literal(next)
					i += 2
				}

				c == '$' -> dollar()
				c == '`' -> throw IllegalArgumentException(SUBSTITUTION)
				else -> {
					literal(c)
					i++
				}
			}
		}
		i++
	}

	private fun dollar() {
		val end = variables.end(i)
		when {
			text.startsWith(PWD, i) -> {
				word.append("\${PWD}")
				i += PWD.length
			}

			text.getOrNull(i + 1) == '(' -> throw IllegalArgumentException(SUBSTITUTION)
			end > i -> {
				word.append(text, i, end)
				i = end
			}

			else -> {
				literal('$')
				i++
			}
		}
		inWord = true
	}

	/** `&&` and `||` end a command; a lone `&` or `|` is a background job or a pipe. */
	private fun operator(c: Char) {
		require(text.getOrNull(i + 1) == c) { REDIRECT }
		endCommand()
		i += 2
	}

	private fun literal(c: Char) {
		if (c == '$') word.append("$$") else word.append(c)
		inWord = true
	}

	private fun endWord() {
		if (inWord) words += word.toString()
		word.setLength(0)
		inWord = false
	}

	private fun endCommand() {
		endWord()
		if (words.isNotEmpty()) commands += words
		words = mutableListOf()
	}
}

/** The variable references in [text], `$NAME` and `${…}` with any nesting, which shells and Compose read alike. */
internal class Variables(private val text: String) {
	/** Each `{` with the index of its matching `}`, matched once up front so that no lookup scans the text again. */
	private val closing = IntArray(text.length) { -1 }

	init {
		val open = ArrayDeque<Int>()
		text.forEachIndexed { i, c ->
			when (c) {
				'{' -> open.addLast(i)
				'}' -> open.removeLastOrNull()?.let { closing[it] = i }
			}
		}
	}

	/** The end of the reference whose `$` is at [start], or [start] when none starts there. */
	fun end(start: Int): Int {
		val first = start + 1
		if (text.getOrNull(first) == '{') return if (closing[first] < 0) start else closing[first] + 1
		var end = first
		while (end < text.length && (text[end] == '_' || text[end].isAsciiLetterOrDigit())) end++
		return if (end > first && !text[first].isDigit()) end else start
	}

	private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

/**
 * A Compose value as one shell word: bare when it is safe, single-quoted when it holds no variables, and otherwise
 * double-quoted with its `${VAR}` and `$VAR` left for the shell. Compose's `$$` is a plain `$`.
 */
internal fun shellWord(value: String): String {
	if (safe.matches(value)) return value
	val variables = Variables(value)
	val plain = StringBuilder()
	val quoted = StringBuilder()
	var hasVariables = false
	var i = 0
	while (i < value.length) {
		val c = value[i]
		val end = if (c == '$') variables.end(i) else i
		when {
			value.startsWith("$$", i) -> {
				plain.append('$')
				quoted.append("\\$")
				i += 2
			}

			end > i -> {
				quoted.append(value, i, end)
				hasVariables = true
				i = end
			}

			else -> {
				plain.append(c)
				if (c in "\"\\$`") quoted.append('\\')
				quoted.append(c)
				i++
			}
		}
	}
	return if (hasVariables) "\"$quoted\"" else "'${plain.toString().replace("'", "'\\''")}'"
}
