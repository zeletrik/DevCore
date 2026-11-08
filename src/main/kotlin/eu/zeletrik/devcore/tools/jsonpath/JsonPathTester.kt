package eu.zeletrik.devcore.tools.jsonpath

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.EvaluationListener
import com.jayway.jsonpath.EvaluationListener.EvaluationContinuation
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.Jackson3JsonNodeJsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import eu.zeletrik.devcore.shell.BoundedWriter
import eu.zeletrik.devcore.shell.MAX_OUTPUT_CHARS
import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.json.JsonFormatter
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Goessner-style JSONPath over a Jackson tree, so numbers keep every digit; the result is always an array. */
object JsonPathTester {
	private const val REGEX_FILTERS = "Regex filters (=~) aren't supported; use the Regex Tester"
	private const val FUNCTIONS = "Path functions aren't supported"
	private const val DEEP_SCANS = "Only one deep scan (..) per path is supported"
	private const val TOO_MANY = "Too many matches to show; narrow the path"

	/** Far beyond any real path; Jayway upper-cases filter operators, which is slow on long text dense in ß. */
	private const val MAX_PATH_CHARS = 10_000
	private const val MAX_MATCHES = 100_000
	private val deepScan = Regex("""\.\.""")
	private val function = Regex("""\.[\p{L}\p{Nd}_]+\(""")
	private val mapper = JsonMapper.builder()
		.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.build()
	private val configuration = Configuration.builder()
		.jsonProvider(Jackson3JsonNodeJsonProvider(mapper))
		.mappingProvider(Jackson3MappingProvider(mapper))
		.options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
		.build()

	fun evaluate(json: String, path: String): String {
		val matches = read(compile(path), parse(json))
		// The compact form is capped too: `$..*` repeats every subtree, so even it can grow far past the input.
		val compact = BoundedWriter().also { mapper.writeValue(it, matches) }.toString()
		return JsonFormatter.format(compact)
	}

	private fun compile(path: String): JsonPath {
		unsupported(path)?.let { throw FieldError("path", it) }
		return try {
			JsonPath.compile(path)
		} catch (e: InvalidPathException) {
			throw FieldError("path", "Invalid JSONPath: ${e.message}", e)
		}
	}

	private fun parse(json: String): JsonNode = try {
		mapper.readTree(json)
	} catch (e: JacksonException) {
		throw JsonFormatter.invalid(e)
	}

	private fun read(path: JsonPath, document: JsonNode): JsonNode {
		val budget = MatchBudget()
		val matches: JsonNode? = path.read(document, configuration.addEvaluationListeners(budget))
		if (budget.exceeded) throw FieldError("path", TOO_MANY)
		return matches ?: mapper.createArrayNode()
	}

	/**
	 * Why [path] isn't supported, or null. Refused: `=~` (Jayway runs that regex with no time limit), path functions
	 * such as `.length()` (which ALWAYS_RETURN_LIST turns into a silent `[]`; functions inside `[?(…)]` filters stay),
	 * and a second deep scan (each `..` multiplies the nodes visited, so `$..*..*..*` on a short, deep document visits
	 * billions). Text inside quotes is never taken for any of them.
	 */
	private fun unsupported(path: String): String? {
		if (path.length > MAX_PATH_CHARS) return "Path is too long (max 10,000 characters)"
		val syntax = withoutQuotedText(path)
		return when {
			"=~" in syntax -> REGEX_FILTERS
			deepScan.findAll(syntax).count() > 1 -> DEEP_SCANS
			hasFunction(syntax) -> FUNCTIONS
			else -> null
		}
	}

	/** [path] with the inside of every quoted string blanked out, keeping every position. */
	private fun withoutQuotedText(path: String): String {
		val syntax = StringBuilder(path)
		var i = 0
		while (i < path.length) {
			if (path[i] == '\'' || path[i] == '"') {
				val end = closingQuote(path, i)
				for (j in i + 1 until end) syntax[j] = ' '
				i = end
			}
			i++
		}
		return syntax.toString()
	}

	private fun closingQuote(path: String, start: Int): Int {
		var i = start + 1
		while (i < path.length && path[i] != path[start]) i += if (path[i] == '\\') 2 else 1
		return minOf(i, path.length)
	}

	/** A `.name(` outside every `[…]`. */
	private fun hasFunction(syntax: String): Boolean {
		var depth = 0
		syntax.forEachIndexed { i, c ->
			when (c) {
				'[' -> depth++
				']' -> depth = maxOf(0, depth - 1)
				'.' -> if (depth == 0 && function.matchesAt(syntax, i)) return true
			}
		}
		return false
	}

	/**
	 * Stops evaluation past [MAX_MATCHES] matches, or once their paths add up to the output cap: Jayway builds and keeps
	 * a path string per match, and a path through long keys can be as long as the document.
	 */
	private class MatchBudget : EvaluationListener {
		var exceeded = false
		private var count = 0
		private var pathChars = 0L

		override fun resultFound(found: EvaluationListener.FoundResult): EvaluationContinuation {
			pathChars += found.path().length
			exceeded = ++count > MAX_MATCHES || pathChars > MAX_OUTPUT_CHARS
			return if (exceeded) EvaluationContinuation.ABORT else EvaluationContinuation.CONTINUE
		}
	}
}
