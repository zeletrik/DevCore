package eu.zeletrik.devcore.tools.jsonpath

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.Jackson3JsonNodeJsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.json.JsonFormatter
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Goessner-style JSONPath over a Jackson tree, so numbers keep every digit; the result is always an array. */
object JsonPathTester {
	private val mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()
	private val configuration = Configuration.builder()
		.jsonProvider(Jackson3JsonNodeJsonProvider(mapper))
		.mappingProvider(Jackson3MappingProvider(mapper))
		.options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
		.build()

	fun evaluate(json: String, path: String): String {
		val compiled = try {
			JsonPath.compile(path)
		} catch (e: InvalidPathException) {
			throw FieldError("path", "Invalid JSONPath: ${e.message}")
		}
		val document = try {
			mapper.readTree(json)
		} catch (e: JacksonException) {
			throw IllegalArgumentException(e.location?.let { "Invalid JSON at line ${it.lineNr}, column ${it.columnNr}: ${e.originalMessage}" } ?: "Invalid JSON: ${e.originalMessage}")
		}
		val matches: JsonNode = compiled.read(document, configuration) ?: mapper.createArrayNode()
		return JsonFormatter.format(mapper.writeValueAsString(matches))
	}
}
