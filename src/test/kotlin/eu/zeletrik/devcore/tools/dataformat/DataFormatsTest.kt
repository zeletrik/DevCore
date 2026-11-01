package eu.zeletrik.devcore.tools.dataformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataFormatsTest {
	private fun convert(text: String, from: Format, to: Format) = DataFormats.convert(text, from, to)

	private fun fails(text: String, from: Format, to: Format) =
		assertFailsWith<IllegalArgumentException> { convert(text, from, to) }.message!!

	/** An array nested [depth] deep around [items] zeros: tiny input, deeply indented output. */
	private fun deepJson(depth: Int, items: Int) =
		"[".repeat(depth) + List(items) { "0" }.joinToString(",") + "]".repeat(depth)

	@Test
	fun `json and yaml both ways, and reformatting`() {
		assertEquals("a:\n  b: 1\nc: true", convert("""{"a":{"b":1},"c":true}""", Format.JSON, Format.YAML))
		assertEquals("b: false", convert("""{"b":false}""", Format.JSON, Format.YAML))
		assertEquals("{\n  \"a\": {\n    \"b\": 1\n  }\n}", convert("a:\n b: 1", Format.YAML, Format.JSON))
		assertEquals("{\n  \"a\": [\n    1,\n    2\n  ]\n}", convert("""{"a":[1,2]}""", Format.JSON, Format.JSON))
		assertEquals("b: 1\na: 2", convert("""{"b":1,"a":2}""", Format.JSON, Format.YAML))
		assertEquals("", convert("", Format.JSON, Format.YAML))
	}

	@Test
	fun `numbers keep every digit, whatever the source format`() {
		val yaml = convert(
			"""{"n":12345678901234567890,"d":0.1000000000000000055511151231257827}""",
			Format.JSON,
			Format.YAML,
		)
		// The key `n` comes out quoted: YAML 1.1 readers would take a bare n for boolean false.
		assertEquals(
			listOf("12345678901234567890", "0.1000000000000000055511151231257827"),
			yaml.lines().map { it.substringAfter(": ") },
		)
		assertEquals("{\n  \"n\": 9223372036854775807\n}", convert("n = 9223372036854775807", Format.TOML, Format.JSON))
		assertEquals(
			"{\n  \"d\": 0.1000000000000000055511151231257827\n}",
			convert("d: 0.1000000000000000055511151231257827", Format.YAML, Format.JSON),
		)
	}

	@Test
	fun `yaml writes plain strings and indented sequences, and quotes only what a loader would read as another type`() {
		assertEquals(
			"name: devcore\ntags:\n  - a\n  - b",
			convert("""{"name":"devcore","tags":["a","b"]}""", Format.JSON, Format.YAML),
		)
		val lookalikes = """{"v":"1.0","t":"true","p":"8080:80","q":"22:22","h":"0x1F","o":"0o17",""" +
			""""u":"1_000","y":"yes","n":".NaN","d":"2026-10-30","s":"~"}"""
		convert(lookalikes, Format.JSON, Format.YAML).lines().forEach { line ->
			val value = line.substringAfter(": ")
			assertTrue(value.startsWith("\"") || value.startsWith("'"), line)
		}
	}

	@Test
	fun `yaml binary becomes Base64`() =
		assertEquals("{\n  \"data\": \"aGVsbG8=\"\n}", convert("data: !!binary aGVsbG8=", Format.YAML, Format.JSON))

	@Test
	fun `toml layout and integer bounds`() {
		assertEquals(
			"title = 'x'\nowner.name = 'y'\nservers = [{port = 80}]",
			convert("""{"title":"x","owner":{"name":"y"},"servers":[{"port":80}]}""", Format.JSON, Format.TOML),
		)
		assertEquals(
			"max = 9223372036854775807\nmin = -9223372036854775808",
			convert("""{"max":9223372036854775807,"min":-9223372036854775808}""", Format.JSON, Format.TOML),
		)
		assertEquals("TOML integers are 64-bit (at 'n')", fails("""{"n":9223372036854775808}""", Format.JSON, Format.TOML))
		assertEquals("TOML integers are 64-bit (at 'n')", fails("""{"n":-9223372036854775809}""", Format.JSON, Format.TOML))
		assertEquals("TOML needs an object at the top level", fails("[1,2]", Format.JSON, Format.TOML))
		assertEquals("TOML has no null value (at 'a.b')", fails("""{"a":{"b":null}}""", Format.JSON, Format.TOML))
	}

	@Test
	fun `properties escape a leading space and refuse what they can't hold`() {
		assertEquals("a.b=c\nl[0]=1\nl[1]=2", convert("""{"a":{"b":"c"},"l":[1,2]}""", Format.JSON, Format.PROPERTIES))
		assertEquals("{\n  \"port\": \"8080\"\n}", convert("port=8080", Format.PROPERTIES, Format.JSON))
		assertEquals("e=\\ lead", convert("""{"e":" lead"}""", Format.JSON, Format.PROPERTIES))
		assertEquals("", convert("{}", Format.JSON, Format.PROPERTIES))
		assertEquals("Properties needs an object at the top level", fails("42", Format.JSON, Format.PROPERTIES))
		assertEquals("Properties can't hold the empty object at 'a'", fails("""{"a":{}}""", Format.JSON, Format.PROPERTIES))
		assertEquals("Properties can't hold the empty array at 'a'", fails("""{"a":[]}""", Format.JSON, Format.PROPERTIES))
		assertEquals("Key 'a.b' would be written twice", fails("""{"a.b":1,"a":{"b":2}}""", Format.JSON, Format.PROPERTIES))
	}

	@Test
	fun csv() {
		assertEquals("a,b,c\n1,x,\n,y,true", convert("""[{"a":1,"b":"x"},{"b":"y","c":true}]""", Format.JSON, Format.CSV))
		assertEquals("v\n\"x,\"\"y\"\"\"", convert("""[{"v":"x,\"y\""}]""", Format.JSON, Format.CSV))
		assertTrue("\r" !in convert("""[{"a":1},{"a":2}]""", Format.JSON, Format.CSV))
		assertEquals("", convert("[]", Format.JSON, Format.CSV))
		assertEquals("", convert("[{}]", Format.JSON, Format.CSV))
		assertEquals(
			"[\n  {\n    \"name\": \"Ada\",\n    \"age\": \"36\"\n  }\n]",
			convert("name,age\nAda,36", Format.CSV, Format.JSON),
		)
		assertEquals("", convert("\n\n", Format.CSV, Format.JSON))
	}

	@Test
	fun `csv ignores any number of trailing blank lines`() {
		assertEquals(
			"[\n  {\n    \"a\": \"1\",\n    \"b\": \"2\"\n  }\n]",
			convert("a,b\n1,2" + "\n".repeat(3_000), Format.CSV, Format.JSON),
		)
		assertEquals(
			"[\n  {\n    \"a\": \"1\",\n    \"b\": \"2  \"\n  }\n]",
			convert("a,b\r\n1,2  \r\n  \r\n", Format.CSV, Format.JSON),
		)
	}

	@Test
	fun `csv errors name the rule, the column or the row's own line`() {
		val flat = "CSV needs an array of objects with plain values"
		assertEquals(flat, fails("{}", Format.JSON, Format.CSV))
		assertEquals("$flat (row 1)", fails("[1]", Format.JSON, Format.CSV))
		assertEquals("$flat (row 1, 'a')", fails("""[{"a":{"b":1}}]""", Format.JSON, Format.CSV))
		assertEquals("Duplicate column 'a'", fails("a,a\n1,2", Format.CSV, Format.JSON))
		assertEquals("Duplicate column ''", fails("a,,\n1,2,3", Format.CSV, Format.JSON))
		assertEquals("Not enough column values: expected 2, found 1 (line 2)", fails("a,b\n1\n3,4", Format.CSV, Format.JSON))
		assertEquals(
			"Not enough column values: expected 2, found 1 (line 3)",
			fails("a,b\n1,2\n3\n5,6", Format.CSV, Format.JSON),
		)
		assertEquals("Too many column values: expected 2, found 3 (line 2)", fails("a,b\n1,2,3", Format.CSV, Format.JSON))
	}

	@Test
	fun `errors - invalid input, duplicates, aliases, extra content and non-finite numbers`() {
		assertTrue(fails("""{"a":}""", Format.JSON, Format.YAML).contains("(line 1, column"))
		assertEquals("Unexpected content after the value (line 1, column 4)", fails("{} {}", Format.JSON, Format.YAML))
		assertEquals("Duplicate key 'a'", fails("""{"a":1,"a":2}""", Format.JSON, Format.YAML))
		assertEquals("Duplicate key 'a'", fails("a: 1\na: 2", Format.YAML, Format.JSON))
		assertEquals(
			"YAML aliases aren't supported (line 2, column 7)",
			fails("base: &b {x: 1}\ncopy: *b", Format.YAML, Format.JSON),
		)
		val merged = fails("base: &b {x: 1}\ncopy: {<<: *b}", Format.YAML, Format.JSON)
		assertTrue(merged.startsWith("YAML aliases aren't supported"))
		assertEquals("Only one YAML document is supported", fails("a: 1\n---\nb: 2", Format.YAML, Format.JSON))
		assertEquals("{\n  \"a\": 1\n}", convert("a: &x 1", Format.YAML, Format.JSON))
		assertEquals("Infinity and NaN aren't supported", fails("x: .inf", Format.YAML, Format.JSON))
		assertEquals("Infinity and NaN aren't supported", fails("x = nan", Format.TOML, Format.JSON))
		val unwritable = fails("""{"a":"\ud83d"}""", Format.JSON, Format.TOML)
		assertTrue(unwritable.isNotEmpty(), "write errors are shown, not thrown raw")
	}

	@Test
	fun `outputs stop at the cap while they are written`() {
		val tooLarge = "Output is too large (max 5,000,000 characters)"
		assertEquals(tooLarge, fails(deepJson(450, 30_000), Format.JSON, Format.YAML))
		val rows = (1..3_000).joinToString(",", "[", "]") { """{"k$it":1}""" }
		assertEquals(tooLarge, fails(rows, Format.JSON, Format.CSV))
		val deep = (1..400).joinToString("") { """{"key$it":""" } + (1..2_000).joinToString(",", "[", "]") { "0" } +
			"}".repeat(400)
		assertEquals(tooLarge, fails(deep, Format.JSON, Format.PROPERTIES))
	}
}
