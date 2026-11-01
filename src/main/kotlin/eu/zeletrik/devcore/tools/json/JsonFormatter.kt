package eu.zeletrik.devcore.tools.json

import eu.zeletrik.devcore.shell.BoundedWriter
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.TokenStreamLocation
import tools.jackson.core.exc.StreamConstraintsException
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.core.util.Separators
import tools.jackson.databind.json.JsonMapper

enum class Indent(val label: String, val spaces: Int) {
	TWO("2 spaces", spaces = 2), FOUR("4 spaces", spaces = 4), MINIFIED("Minified", spaces = 0)
}

private const val MAX_UNQUOTE_PASSES = 32
private val BYTE_ORDER_MARK = Char(0xFEFF).toString()
private val FORM_FEED = Char(12)

object JsonFormatter {
	private val mapper = JsonMapper()

	/**
	 * Streams token by token, so member order, duplicate names and number literals survive untouched. With [expand],
	 * a string holding exactly one JSON object or array is written as that structure, at any depth (an SNS message
	 * inside an SQS body, a Docker log line), and escaped text pasted without its quotes is read as that string.
	 */
	fun format(text: String, indent: Indent = Indent.TWO, expand: Boolean = false): String = try {
		write(text.removePrefix(BYTE_ORDER_MARK), indent, expand)
	} catch (e: JacksonException) {
		val retry = if (expand && e !is StreamConstraintsException) unquoted(text) else null
		retry?.let { format(it, indent, expand = true) } ?: throw invalid(e)
	}

	private fun write(text: String, indent: Indent, expand: Boolean): String {
		val out = BoundedWriter()
		val writer = if (indent == Indent.MINIFIED) mapper.writer() else mapper.writer().with(prettyPrinter(indent.spaces))
		mapper.createParser(text).use { parser ->
			if (parser.nextToken() != null) {
				writer.createGenerator(out).use { generator -> copyValue(parser, generator, expand) }
				if (parser.nextToken() != null) {
					throw invalid(parser.currentTokenLocation(), "unexpected content after the JSON value")
				}
			}
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
				token == JsonToken.VALUE_STRING && LoneSurrogates.hasLoneSurrogate(parser.string) ->
					generator.writeRawValue(LoneSurrogates.escaped(parser.string))
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
	 * escaped twice takes two passes); null when it never gets there within [MAX_UNQUOTE_PASSES] passes.
	 */
	private fun unquoted(text: String): String? =
		generateSequence(text) { current ->
			val trimmed = current.trim()
			stringContent(trimmed)?.takeIf { it != trimmed }
		}
			.drop(1)
			.take(MAX_UNQUOTE_PASSES)
			.firstOrNull(::isContainer)

	/** [text] read as the content of a JSON string literal, or null when it isn't one. */
	private fun stringContent(text: String): String? = try {
		mapper.createParser("\"$text\"").use { parser ->
			parser.nextToken()
				?.takeIf { it == JsonToken.VALUE_STRING }
				?.let { parser.string }
				?.takeIf { parser.nextToken() == null }
		}
	} catch (_: JacksonException) {
		null
	}

	private fun invalid(at: TokenStreamLocation?, reason: String, cause: Throwable? = null): IllegalArgumentException {
		val where = at?.let { " at line ${it.lineNr}, column ${it.columnNr}" }.orEmpty()
		return IllegalArgumentException("Invalid JSON$where: $reason", cause)
	}

	/** Expansion stacks embedded documents, so the combined depth is only checked while writing. */
	private fun invalid(e: JacksonException) =
		if (e is StreamConstraintsException && "StreamWriteConstraints" in e.originalMessage) {
			IllegalArgumentException("Invalid JSON: nested too deeply (max 500 levels)", e)
		} else {
			invalid(e.location, e.originalMessage, e)
		}

	private fun prettyPrinter(spaces: Int): DefaultPrettyPrinter {
		val indenter = DefaultIndenter(" ".repeat(spaces), "\n")
		val separators = Separators.createDefaultInstance()
			.withObjectNameValueSpacing(Separators.Spacing.AFTER)
			.withObjectEmptySeparator("")
			.withArrayEmptySeparator("")
		return DefaultPrettyPrinter(separators).withObjectIndenter(indenter).withArrayIndenter(indenter)
	}
}

/** Strings with an unpaired surrogate, which can only be written as JSON with that surrogate escaped. */
private object LoneSurrogates {
	fun hasLoneSurrogate(text: String) = text.indices.any { isLone(text, it) }

	private fun isLone(text: String, i: Int) = when {
		text[i].isHighSurrogate() -> text.getOrNull(i + 1)?.isLowSurrogate() != true
		text[i].isLowSurrogate() -> text.getOrNull(i - 1)?.isHighSurrogate() != true
		else -> false
	}

	/** A quoted JSON string whose unpaired surrogates stay escaped (`\uD83D`), since a raw one isn't valid text. */
	fun escaped(text: String): String = buildString {
		append('"')
		text.forEachIndexed { i, c ->
			when {
				c == '"' -> append("\\\"")
				c == '\\' -> append("\\\\")
				c == '\n' -> append("\\n")
				c == '\r' -> append("\\r")
				c == '\t' -> append("\\t")
				c == '\b' -> append("\\b")
				c == FORM_FEED -> append("\\f")
				c < ' ' || isLone(text, i) -> append("\\u%04X".format(c.code))
				else -> append(c)
			}
		}
		append('"')
	}
}
