package eu.zeletrik.devcore.tools.json

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.TokenStreamLocation
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.core.util.Separators
import tools.jackson.databind.json.JsonMapper
import java.io.StringWriter

enum class Indent(val label: String, val spaces: Int) {
	TWO("2 spaces", 2), FOUR("4 spaces", 4), MINIFIED("Minified", 0)
}

object JsonFormatter {
	private val mapper = JsonMapper()

	/**
	 * Streams token by token, so member order, duplicate names and number literals survive untouched. With [expand],
	 * a string holding exactly one JSON object or array is written as that structure, at any depth (an SNS message
	 * inside an SQS body, a Docker log line), and escaped text pasted without its quotes is read as that string.
	 */
	fun format(text: String, indent: Indent = Indent.TWO, expand: Boolean = false): String {
		val out = StringWriter()
		val writer = if (indent == Indent.MINIFIED) mapper.writer() else mapper.writer().with(prettyPrinter(indent.spaces))
		try {
			mapper.createParser(text).use { parser ->
				if (parser.nextToken() == null) return ""
				writer.createGenerator(out).use { generator -> copyValue(parser, generator, expand) }
				if (parser.nextToken() != null) throw invalid(parser.currentTokenLocation(), "unexpected content after the JSON value")
			}
		} catch (e: JacksonException) {
			if (expand) unquoted(text)?.let { return format(it, indent, expand = true) }
			throw invalid(e.location, e.originalMessage)
		}
		return out.toString()
	}

	/** Copies the value at the parser's current token, expanding embedded JSON when asked. */
	private fun copyValue(parser: JsonParser, generator: JsonGenerator, expand: Boolean) {
		var token = parser.currentToken()
		var depth = 0
		while (true) {
			when {
				token.isNumeric -> generator.writeNumber(parser.string)
				expand && token == JsonToken.VALUE_STRING && isContainer(parser.string) ->
					mapper.createParser(parser.string).use { inner ->
						inner.nextToken()
						copyValue(inner, generator, expand = true)
					}
				else -> generator.copyCurrentEvent(parser)
			}
			if (token.isStructStart) depth++ else if (token.isStructEnd) depth--
			if (depth == 0) break
			token = parser.nextToken()
		}
	}

	/** Whether [text] is exactly one valid JSON object or array; anything else stays a string. */
	private fun isContainer(text: String): Boolean {
		val first = text.firstOrNull { !it.isWhitespace() }
		if (first != '{' && first != '[') return false
		return try {
			mapper.createParser(text).use { parser ->
				parser.nextToken()
				parser.skipChildren()
				parser.nextToken() == null
			}
		} catch (_: JacksonException) {
			false
		}
	}

	/**
	 * Escaped text copied from inside a JSON string, without its quotes, decoded until it is an object or array (text
	 * escaped twice takes two passes); null when it never gets there. Each pass shortens the text, so this ends.
	 */
	private fun unquoted(text: String): String? {
		val trimmed = text.trim()
		val decoded = try {
			mapper.createParser("\"${trimmed}\"").use { parser ->
				parser.nextToken()?.takeIf { it == JsonToken.VALUE_STRING }?.let { parser.string }?.takeIf { parser.nextToken() == null }
			}
		} catch (_: JacksonException) {
			null
		}
		return when {
			decoded == null || decoded == trimmed -> null
			isContainer(decoded) -> decoded
			else -> unquoted(decoded)
		}
	}

	private fun invalid(location: TokenStreamLocation?, reason: String) = IllegalArgumentException(
		if (location == null) "Invalid JSON: $reason"
		else "Invalid JSON at line ${location.lineNr}, column ${location.columnNr}: $reason",
	)

	private fun prettyPrinter(spaces: Int): DefaultPrettyPrinter {
		val indenter = DefaultIndenter(" ".repeat(spaces), "\n")
		val separators = Separators.createDefaultInstance()
			.withObjectNameValueSpacing(Separators.Spacing.AFTER)
			.withObjectEmptySeparator("")
			.withArrayEmptySeparator("")
		return DefaultPrettyPrinter(separators).withObjectIndenter(indenter).withArrayIndenter(indenter)
	}
}
