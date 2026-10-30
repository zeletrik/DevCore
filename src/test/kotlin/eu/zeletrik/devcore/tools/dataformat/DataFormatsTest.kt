package eu.zeletrik.devcore.tools.dataformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataFormatsTest {
	private fun convert(text: String, from: Format, to: Format) = DataFormats.convert(text, from, to)

	private fun fails(text: String, from: Format, to: Format) =
		assertFailsWith<IllegalArgumentException> { convert(text, from, to) }.message!!

	@Test
	fun `json and yaml both ways, and reformatting`() {
		assertEquals("a:\n  b: 1\nc: true", convert("""{"a":{"b":1},"c":true}""", Format.JSON, Format.YAML))
		assertEquals("{\n  \"a\": {\n    \"b\": 1\n  }\n}", convert("a:\n b: 1", Format.YAML, Format.JSON))
		assertEquals("{\n  \"a\": [\n    1,\n    2\n  ]\n}", convert("""{"a":[1,2]}""", Format.JSON, Format.JSON))
		assertEquals("b: 1\na: 2", convert("""{"b":1,"a":2}""", Format.JSON, Format.YAML))
	}

	@Test
	fun `numbers keep every digit, whatever the source format`() {
		val yaml = convert("""{"n":12345678901234567890,"d":0.1000000000000000055511151231257827}""", Format.JSON, Format.YAML)
		// The key `n` comes out quoted: YAML 1.1 readers would take a bare n for boolean false.
		assertEquals(listOf("12345678901234567890", "0.1000000000000000055511151231257827"), yaml.lines().map { it.substringAfter(": ") })
		assertEquals("{\n  \"n\": 9223372036854775807\n}", convert("n = 9223372036854775807", Format.TOML, Format.JSON))
		assertEquals("{\n  \"d\": 0.1000000000000000055511151231257827\n}", convert("d: 0.1000000000000000055511151231257827", Format.YAML, Format.JSON))
	}

	@Test
	fun `yaml writes plain strings, indented sequences and quotes only where needed`() {
		assertEquals("name: devcore\ntags:\n  - a\n  - b", convert("""{"name":"devcore","tags":["a","b"]}""", Format.JSON, Format.YAML))
		val quoted = convert("""{"v":"1.0","t":"true"}""", Format.JSON, Format.YAML).lines()
		assertTrue(quoted[0].matches(Regex("""v: ["']1\.0["']""")) && quoted[1].matches(Regex("""t: ["']true["']""")), quoted.toString())
	}

	@Test
	fun `toml layout, quoting and integer bounds`() {
		assertEquals("title = 'x'\nowner.name = 'y'\nservers = [{port = 80}]", convert("""{"title":"x","owner":{"name":"y"},"servers":[{"port":80}]}""", Format.JSON, Format.TOML))
		assertEquals("quote = \"it's\"", convert("""{"quote":"it's"}""", Format.JSON, Format.TOML))
		assertEquals("n = 12345", convert("""{"n":12345}""", Format.JSON, Format.TOML))
		assertEquals("max = 9223372036854775807\nmin = -9223372036854775808", convert("""{"max":9223372036854775807,"min":-9223372036854775808}""", Format.JSON, Format.TOML))
		assertEquals("TOML integers are 64-bit (at 'n')", fails("""{"n":9223372036854775808}""", Format.JSON, Format.TOML))
		assertEquals("TOML integers are 64-bit (at 'n')", fails("""{"n":-9223372036854775809}""", Format.JSON, Format.TOML))
		assertEquals("TOML needs an object at the top level", fails("[1,2]", Format.JSON, Format.TOML))
		assertEquals("TOML has no null value (at 'a.b')", fails("""{"a":{"b":null}}""", Format.JSON, Format.TOML))
	}

	@Test
	fun properties() {
		assertEquals("a.b=c\nl[0]=1\nl[1]=2", convert("""{"a":{"b":"c"},"l":[1,2]}""", Format.JSON, Format.PROPERTIES))
		assertEquals("{\n  \"port\": \"8080\"\n}", convert("port=8080", Format.PROPERTIES, Format.JSON))
		assertEquals("Properties needs an object at the top level", fails("42", Format.JSON, Format.PROPERTIES))
	}

	@Test
	fun csv() {
		assertEquals("a,b,c\n1,x,\n,y,true", convert("""[{"a":1,"b":"x"},{"b":"y","c":true}]""", Format.JSON, Format.CSV))
		assertEquals("v\n\"x,\"\"y\"\"\"", convert("""[{"v":"x,\"y\""}]""", Format.JSON, Format.CSV))
		assertEquals("[\n  {\n    \"name\": \"Ada\",\n    \"age\": \"36\"\n  }\n]", convert("name,age\nAda,36", Format.CSV, Format.JSON))
		assertEquals("[\n  {\n    \"a\": \"1\",\n    \"b\": \"2\"\n  }\n]", convert("a,b\n1,2\n\n", Format.CSV, Format.JSON))
		assertTrue("\r" !in convert("""[{"a":1},{"a":2}]""", Format.JSON, Format.CSV))
		assertTrue(fails("a,b\n1", Format.CSV, Format.JSON).contains("line 2"))
		assertEquals("CSV needs an array of objects with plain values (row 1, 'a')", fails("""[{"a":{"b":1}}]""", Format.JSON, Format.CSV))
	}

	@Test
	fun `errors - invalid input, duplicates, aliases and extra yaml documents`() {
		assertTrue(fails("""{"a":}""", Format.JSON, Format.YAML).contains("(line 1, column"))
		assertEquals("Duplicate key 'a'", fails("""{"a":1,"a":2}""", Format.JSON, Format.YAML))
		assertEquals("Duplicate key 'a'", fails("a: 1\na: 2", Format.YAML, Format.JSON))
		assertEquals("YAML aliases are not supported (line 2, column 7)", fails("base: &b {x: 1}\ncopy: *b", Format.YAML, Format.JSON))
		assertTrue(fails("base: &b {x: 1}\ncopy: {<<: *b}", Format.YAML, Format.JSON).startsWith("YAML aliases are not supported (line 2"))
		assertEquals("Only one YAML document is supported", fails("a: 1\n---\nb: 2", Format.YAML, Format.JSON))
		assertEquals("{\n  \"a\": 1\n}", convert("a: &x 1", Format.YAML, Format.JSON))
	}
}
