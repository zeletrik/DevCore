package eu.zeletrik.devcore.tools.dataformat

import eu.zeletrik.devcore.shell.BoundedWriter
import eu.zeletrik.devcore.tools.json.JsonFormatter
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.ObjectWriter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.dataformat.csv.CsvMapper
import tools.jackson.dataformat.csv.CsvReadFeature
import tools.jackson.dataformat.csv.CsvSchema
import tools.jackson.dataformat.javaprop.JavaPropsMapper
import tools.jackson.dataformat.javaprop.JavaPropsSchema
import tools.jackson.dataformat.toml.TomlMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLParser
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.dataformat.yaml.util.StringQuotingChecker
import java.util.Base64

/** The data formats the converter reads and writes. */
enum class Format(val label: String) { JSON("JSON"), YAML("YAML"), TOML("TOML"), PROPERTIES("Properties"), CSV("CSV") }

/** Every conversion goes through one tree: member order and exact numbers survive, and nothing is dropped silently. */
object DataFormats {
	private val nodes = JsonNodeFactory.instance
	private val json = JsonMapper()
	private val yaml = YAMLMapper.builder(YAMLFactory.builder().stringQuotingChecker(YamlLookalikes).build())
		.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
		.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
		.enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
		.enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
		.build()
	private val toml = TomlMapper()
	private val csv = CsvMapper.builder().enable(CsvReadFeature.FAIL_ON_MISSING_COLUMNS).build()
	private val props = JavaPropsMapper()
	private val propsSchema = JavaPropsSchema.emptySchema().withWriteIndexUsingMarkers(true).withFirstArrayOffset(0)

	fun convert(text: String, from: Format, to: Format): String = try {
		read(text, from)?.let { write(it, to) } ?: ""
	} catch (e: JacksonException) {
		throw invalid(e)
	}

	private fun invalid(e: JacksonException) =
		IllegalArgumentException(
			e.location?.let { "${e.originalMessage} (line ${it.lineNr}, column ${it.columnNr})" } ?: e.originalMessage,
			e,
		)

	private fun read(text: String, from: Format): JsonNode? = when (from) {
		Format.JSON -> StrictTree.read(json, text, from)
		Format.YAML -> StrictTree.read(yaml, text, from)
		Format.TOML -> StrictTree.read(toml, text, from)
		Format.PROPERTIES -> props.readerFor(JsonNode::class.java).with(propsSchema).readValue(text)
		Format.CSV -> csvRows(withoutTrailingBlankLines(text))
	}

	private fun write(tree: JsonNode, to: Format): String = when (to) {
		Format.JSON -> JsonFormatter.format(json.writeValueAsString(tree))
		Format.YAML -> bounded(yaml.writer(), tree)
		Format.TOML -> {
			require(tree.isObject) { "TOML needs an object at the top level" }
			checkToml(tree, "")
			bounded(toml.writer(), tree)
		}
		Format.PROPERTIES -> {
			require(tree.isObject) { "Properties needs an object at the top level" }
			checkProperties(tree, "", mutableSetOf())
			escapeLeadingSpaces(bounded(props.writer(propsSchema), tree))
		}
		Format.CSV -> csvOf(tree)
	}

	/** Writes through the output cap, so a small input with deep nesting can't build a huge string first. */
	private fun bounded(writer: ObjectWriter, tree: JsonNode) =
		BoundedWriter().also { writer.writeValue(it, tree) }.toString().trimEnd('\n')

	private fun checkToml(node: JsonNode, path: String) {
		when {
			node.isNull -> throw IllegalArgumentException("TOML has no null value (at '$path')")
			node.isIntegralNumber && !node.canConvertToLong() ->
				throw IllegalArgumentException("TOML integers are 64-bit (at '$path')")
			node.isObject ->
				node.properties().forEach { (name, child) -> checkToml(child, if (path.isEmpty()) name else "$path.$name") }
			node.isArray -> node.forEachIndexed { i, child -> checkToml(child, "$path[$i]") }
		}
	}

	private fun csvOf(tree: JsonNode): String {
		val flat = "CSV needs an array of objects with plain values"
		require(tree.isArray) { flat }
		val columns = linkedSetOf<String>()
		tree.forEachIndexed { i, row ->
			require(row.isObject) { "$flat (row ${i + 1})" }
			row.properties().forEach { (name, value) ->
				require(!value.isContainer) { "$flat (row ${i + 1}, '$name')" }
				columns += name
			}
		}
		if (columns.isEmpty()) return ""
		val schema = CsvSchema.builder().addColumns(columns, CsvSchema.ColumnType.STRING).setUseHeader(true).build()
		return bounded(csv.writer(schema), tree)
	}

	/** CSV read row by row: the header is checked for duplicates, and each row's error names its own line. */
	private fun csvRows(text: String): JsonNode? {
		if (text.isEmpty()) return null
		val rows = csv.readerFor(Array<String>::class.java).with(CsvReadFeature.WRAP_AS_ARRAY).readValues<Array<String>>(text)
		val header = rows.nextValue()
		val seen = hashSetOf<String>()
		val duplicate = header.firstOrNull { !seen.add(it) }
		require(duplicate == null) { "Duplicate column '$duplicate'" }
		val array = nodes.arrayNode()
		while (rows.hasNextValue()) {
			val line = rows.currentLocation().lineNr
			val row = rows.nextValue()
			if (row.size != header.size) {
				val kind = if (row.size < header.size) "Not enough" else "Too many"
				throw IllegalArgumentException("$kind column values: expected ${header.size}, found ${row.size} (line $line)")
			}
			array.add(nodes.objectNode().also { obj -> header.forEachIndexed { i, name -> obj.put(name, row[i]) } })
		}
		return array
	}

	/** A text area usually ends with a newline, and trailing blank lines are not rows. Only the blank tail is scanned. */
	private fun withoutTrailingBlankLines(text: String): String {
		var end = text.length
		while (true) {
			val newline = text.lastIndexOf('\n', end - 1)
			if (newline < 0 || !text.substring(newline + 1, end).all { it in " \t\r" }) break
			end = if (newline > 0 && text[newline - 1] == '\r') newline - 1 else newline
		}
		return text.substring(0, end)
	}

	/** Properties can't hold an empty object or array (an empty top level is fine), or two values under one key. */
	private fun checkProperties(node: JsonNode, path: String, keys: MutableSet<String>) {
		val emptyContainer = node.isContainer && node.isEmpty && (node.isArray || path.isNotEmpty())
		when {
			emptyContainer -> {
				val kind = if (node.isArray) "array" else "object"
				throw IllegalArgumentException("Properties can't hold the empty $kind at '$path'")
			}
			node.isObject -> node.properties()
				.forEach { (name, child) -> checkProperties(child, if (path.isEmpty()) name else "$path.$name", keys) }
			node.isArray -> node.forEachIndexed { i, child -> checkProperties(child, "$path[$i]", keys) }
			else -> require(keys.add(path)) { "Key '$path' would be written twice" }
		}
	}

	/** java.util.Properties drops leading spaces of a value, so they are escaped after the separator. */
	private fun escapeLeadingSpaces(text: String) = text.lines().joinToString("\n") { line ->
		var i = 0
		while (i < line.length && line[i] != '=') i += if (line[i] == '\\') 2 else 1
		if (i + 1 < line.length && line[i + 1] == ' ') line.substring(0, i + 1) + "\\" + line.substring(i + 1) else line
	}
}

/** Builds the tree token by token, so duplicate keys and YAML aliases are caught instead of silently lost. */
private object StrictTree {
	private val nodes = JsonNodeFactory.instance
	private val nonFinite = Regex("[-+]?\\.?(inf|nan)", RegexOption.IGNORE_CASE)

	fun read(mapper: ObjectMapper, text: String, from: Format): JsonNode? = mapper.createParser(text).use { parser ->
		val first = parser.nextToken()
		val root = first?.let { node(parser, it) }
		if (root != null && parser.nextToken() != null) {
			require(from != Format.YAML) { "Only one YAML document is supported" }
			fail(parser, "Unexpected content after the value")
		}
		root
	}

	private fun node(parser: JsonParser, token: JsonToken): JsonNode {
		if (parser is YAMLParser && parser.isCurrentAlias) fail(parser, "YAML aliases aren't supported")
		return when (token) {
			JsonToken.START_OBJECT -> objectNode(parser)
			JsonToken.START_ARRAY -> arrayNode(parser)
			JsonToken.VALUE_NUMBER_INT -> nodes.numberNode(parser.bigIntegerValue)
			JsonToken.VALUE_NUMBER_FLOAT -> {
				require(!nonFinite.matches(parser.string)) { "Infinity and NaN aren't supported" }
				nodes.numberNode(parser.decimalValue)
			}
			JsonToken.VALUE_TRUE -> nodes.booleanNode(true)
			JsonToken.VALUE_FALSE -> nodes.booleanNode(false)
			JsonToken.VALUE_NULL -> nodes.nullNode()
			JsonToken.VALUE_EMBEDDED_OBJECT -> when (val value = parser.embeddedObject) {
				is ByteArray -> nodes.textNode(Base64.getEncoder().encodeToString(value))
				else -> nodes.textNode(value.toString())
			}
			else -> nodes.textNode(parser.string)
		}
	}

	private fun objectNode(parser: JsonParser) = nodes.objectNode().also { obj ->
		while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
			val name = parser.currentName()
			require(!obj.has(name)) { "Duplicate key '$name'" }
			obj.set(name, node(parser, parser.nextToken()))
		}
	}

	private fun arrayNode(parser: JsonParser) = nodes.arrayNode().also { array ->
		var next = parser.nextToken()
		while (next != JsonToken.END_ARRAY) {
			array.add(node(parser, next))
			next = parser.nextToken()
		}
	}

	private fun fail(parser: JsonParser, message: String): Nothing {
		val at = parser.currentTokenLocation()
		throw IllegalArgumentException("$message (line ${at.lineNr}, column ${at.columnNr})")
	}
}

/**
 * Quotes every string a YAML 1.1 or 1.2 loader would read as something else: Jackson's own checks, plus hex, octal,
 * binary and base-60 ints, `_` separators, `.inf`/`.nan`, `~` and dates, so PyYAML and Ansible read them back as text.
 */
private object YamlLookalikes : StringQuotingChecker.Default() {
	private val lookalikes = listOf(
		"~",
		"[-+]?0[bB][0-1_]+",
		"[-+]?0[oO]?[0-7_]+",
		"[-+]?0[xX][0-9a-fA-F_]+",
		// Base-60 needs groups up to 59 (`22:22` is 1342), but any colon-separated digits are quoted to be safe.
		"[-+]?[0-9][0-9_]*(:[0-9]+)+([.][0-9_]*)?",
		"[-+]?([0-9][0-9_]*)?[.]?[0-9_]*([eE][-+]?[0-9]+)?",
		"[-+]?[.](inf|Inf|INF)",
		"[.](nan|NaN|NAN)",
		"[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}" +
			"(([Tt]|[ \t]+)[0-9]{1,2}:[0-9]{2}:[0-9]{2}([.][0-9]*)?([ \t]*(Z|[-+][0-9]{1,2}(:[0-9]{2})?))?)?",
	).map(::Regex)

	override fun needToQuoteValue(value: String) =
		super.needToQuoteValue(value) || (
			value.any { it.isDigit() || it == '~' || it == '.' } && lookalikes.any { it.matches(value) }
		)
}
