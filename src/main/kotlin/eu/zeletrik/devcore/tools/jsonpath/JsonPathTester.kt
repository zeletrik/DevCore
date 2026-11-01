package eu.zeletrik.devcore.tools.jsonpath

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.Jackson3JsonNodeJsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import eu.zeletrik.devcore.shell.BoundedWriter
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
		checkSupported(path)
		val compiled = try {
			JsonPath.compile(path)
		} catch (e: InvalidPathException) {
			throw FieldError("path", "Invalid JSONPath: ${e.message}", e)
		}
		val document = try {
			mapper.readTree(json)
		} catch (e: JacksonException) {
			throw JsonFormatter.invalid(e)
		}
		val matches: JsonNode = compiled.read(document, configuration) ?: mapper.createArrayNode()
		// The compact form is capped too: `$..*` repeats every subtree, so even it can grow far past the input.
		val compact = BoundedWriter().also { mapper.writeValue(it, matches) }.toString()
		return JsonFormatter.format(compact)
	}

	/**
	 * Rejects `=~` (Jayway runs that regex with no time limit) and path functions such as `.length()` (which
	 * ALWAYS_RETURN_LIST turns into a silent `[]`). Quoted strings are skipped; functions inside `[?(…)]` filters stay.
	 */
	private fun checkSupported(path: String) {
		var depth = 0
		var i = 0
		while (i < path.length) {
			val c = path[i]
			when {
				c == '\'' || c == '"' -> i = closingQuote(path, i)
				path.startsWith("=~", i) -> throw FieldError("path", REGEX_FILTERS)
				c == '[' -> depth++
				c == ']' -> depth = maxOf(0, depth - 1)
				c == '.' && depth == 0 -> {
					var end = i + 1
					while (end < path.length && (path[end].isLetterOrDigit() || path[end] == '_')) end++
					if (end > i + 1 && path.getOrNull(end) == '(') throw FieldError("path", FUNCTIONS)
				}
			}
			i++
		}
	}

	private fun closingQuote(path: String, start: Int): Int {
		var i = start + 1
		while (i < path.length && path[i] != path[start]) i += if (path[i] == '\\') 2 else 1
		return minOf(i, path.length)
	}
}
