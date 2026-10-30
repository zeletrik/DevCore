package eu.zeletrik.devcore.tools.dataformat

import eu.zeletrik.devcore.tools.json.JsonFormatter
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.dataformat.csv.CsvMapper
import tools.jackson.dataformat.csv.CsvReadFeature
import tools.jackson.dataformat.csv.CsvSchema
import tools.jackson.dataformat.javaprop.JavaPropsMapper
import tools.jackson.dataformat.javaprop.JavaPropsSchema
import tools.jackson.dataformat.toml.TomlMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLParser
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import java.math.BigInteger

enum class Format(val label: String) { JSON("JSON"), YAML("YAML"), TOML("TOML"), PROPERTIES("Properties"), CSV("CSV") }

/** Every conversion goes through one tree: member order and exact numbers survive, and nothing is dropped silently. */
object DataFormats {
	private val nodes = JsonNodeFactory.instance
	private val json = JsonMapper()
	private val yaml = YAMLMapper.builder()
		.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
		.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
		.enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
		.enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
		.build()
	private val toml = TomlMapper()
	private val csv = CsvMapper.builder().enable(CsvReadFeature.FAIL_ON_MISSING_COLUMNS).build()
	private val props = JavaPropsMapper()
	private val propsSchema = JavaPropsSchema.emptySchema().withWriteIndexUsingMarkers(true).withFirstArrayOffset(0)
	private val longs = BigInteger.valueOf(Long.MIN_VALUE)..BigInteger.valueOf(Long.MAX_VALUE)

	fun convert(text: String, from: Format, to: Format): String {
		val tree = try {
			read(text, from)
		} catch (e: JacksonException) {
			throw IllegalArgumentException(e.location?.let { "${e.originalMessage} (line ${it.lineNr}, column ${it.columnNr})" } ?: e.originalMessage)
		} ?: return ""
		return write(tree, to)
	}

	private fun read(text: String, from: Format): JsonNode? = when (from) {
		Format.JSON -> stream(json, text, from)
		Format.YAML -> stream(yaml, text, from)
		Format.TOML -> stream(toml, text, from)
		Format.PROPERTIES -> props.readerFor(JsonNode::class.java).with(propsSchema).readValue(text)
		Format.CSV -> {
			// A text area usually ends with a newline; trailing empty lines are not rows.
			val rows = text.replace(Regex("(\\r?\\n[ \\t]*)+$"), "")
			nodes.arrayNode().addAll(csv.readerFor(JsonNode::class.java).with(CsvSchema.emptySchema().withHeader()).readValues<JsonNode>(rows).readAll())
		}
	}

	/** Builds the tree token by token, so duplicate keys and YAML aliases are caught instead of silently lost. */
	private fun stream(mapper: ObjectMapper, text: String, from: Format): JsonNode? = mapper.createParser(text).use { parser ->
		val root = node(parser, parser.nextToken() ?: return null)
		if (parser.nextToken() != null) {
			if (from == Format.YAML) throw IllegalArgumentException("Only one YAML document is supported")
			val at = parser.currentTokenLocation()
			throw IllegalArgumentException("Unexpected content after the value (line ${at.lineNr}, column ${at.columnNr})")
		}
		root
	}

	private fun node(parser: JsonParser, token: JsonToken): JsonNode {
		if (parser is YAMLParser && parser.isCurrentAlias) {
			val at = parser.currentTokenLocation()
			throw IllegalArgumentException("YAML aliases are not supported (line ${at.lineNr}, column ${at.columnNr})")
		}
		return when (token) {
			JsonToken.START_OBJECT -> nodes.objectNode().also { obj ->
				while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
					val name = parser.currentName()
					if (obj.has(name)) throw IllegalArgumentException("Duplicate key '$name'")
					obj.set(name, node(parser, parser.nextToken()))
				}
			}
			JsonToken.START_ARRAY -> nodes.arrayNode().also { array ->
				var next = parser.nextToken()
				while (next != JsonToken.END_ARRAY) {
					array.add(node(parser, next))
					next = parser.nextToken()
				}
			}
			JsonToken.VALUE_NUMBER_INT -> nodes.numberNode(parser.bigIntegerValue)
			JsonToken.VALUE_NUMBER_FLOAT -> nodes.numberNode(parser.decimalValue)
			JsonToken.VALUE_TRUE -> nodes.booleanNode(true)
			JsonToken.VALUE_FALSE -> nodes.booleanNode(false)
			JsonToken.VALUE_NULL -> nodes.nullNode()
			JsonToken.VALUE_EMBEDDED_OBJECT -> nodes.textNode(parser.embeddedObject.toString())
			else -> nodes.textNode(parser.string)
		}
	}

	private fun write(tree: JsonNode, to: Format): String = when (to) {
		Format.JSON -> JsonFormatter.format(json.writeValueAsString(tree))
		Format.YAML -> yaml.writeValueAsString(tree).trimEnd('\n')
		Format.TOML -> {
			require(tree.isObject) { "TOML needs an object at the top level" }
			checkToml(tree, "")
			toml.writeValueAsString(tree).trimEnd('\n')
		}
		Format.PROPERTIES -> {
			require(tree.isObject) { "Properties needs an object at the top level" }
			props.writer(propsSchema).writeValueAsString(tree).trimEnd('\n')
		}
		Format.CSV -> csvOf(tree)
	}

	private fun checkToml(node: JsonNode, path: String) {
		when {
			node.isNull -> throw IllegalArgumentException("TOML has no null value (at '$path')")
			node.isIntegralNumber && node.bigIntegerValue() !in longs -> throw IllegalArgumentException("TOML integers are 64-bit (at '$path')")
			node.isObject -> node.properties().forEach { (name, child) -> checkToml(child, if (path.isEmpty()) name else "$path.$name") }
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
		val schema = CsvSchema.builder().addColumns(columns, CsvSchema.ColumnType.STRING).setUseHeader(true).build()
		return csv.writer(schema).writeValueAsString(tree).trimEnd('\n')
	}
}
