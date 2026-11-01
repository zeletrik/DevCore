package eu.zeletrik.devcore.tools.dataformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataFormatFixesTest {
	private fun convert(text: String, from: Format, to: Format) = DataFormats.convert(text, from, to)

	private fun error(text: String, from: Format, to: Format = Format.JSON) =
		assertFailsWith<IllegalArgumentException> { convert(text, from, to) }.message.orEmpty()

	@Test
	fun `empty arrays give empty CSV`() {
		assertEquals("", convert("[]", Format.JSON, Format.CSV))
		assertEquals("", convert("[{}]", Format.JSON, Format.CSV))
	}

	@Test
	fun `write errors are shown, not thrown raw`() {
		assertTrue(error("""{"a":"\ud83d"}""", Format.JSON, Format.TOML).isNotEmpty())
	}

	@Test
	fun `yaml quotes strings that would read back as other types`() {
		val yaml = convert(
			"""{"p":"8080:80","q":"22:22","h":"0x1F","o":"0o17","u":"1_000","y":"yes","n":".NaN","d":"2026-10-30","t":"~"}""",
			Format.JSON,
			Format.YAML,
		)
		yaml.lines().forEach { line ->
			assertTrue(line.substringAfter(": ").startsWith("\"") || line.substringAfter(": ").startsWith("'"), line)
		}
		assertEquals("name: devcore", convert("""{"name":"devcore"}""", Format.JSON, Format.YAML))
	}

	@Test
	fun `duplicate CSV columns are refused`() {
		assertEquals("Duplicate column 'a'", error("a,a\n1,2", Format.CSV))
		assertEquals("Duplicate column ''", error("a,,\n1,2,3", Format.CSV))
	}

	@Test
	fun `yaml binary becomes Base64`() =
		assertEquals("{\n  \"data\": \"aGVsbG8=\"\n}", convert("data: !!binary aGVsbG8=", Format.YAML, Format.JSON))

	@Test
	fun `csv row errors name the row's own line`() {
		assertTrue(error("a,b\n1\n3,4", Format.CSV).endsWith("(line 2)"), error("a,b\n1\n3,4", Format.CSV))
		assertTrue(error("a,b\n1,2\n3\n5,6", Format.CSV).endsWith("(line 3)"))
	}

	@Test
	fun `any number of trailing blank lines`() {
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
	fun `properties escape a leading space and refuse what they can't hold`() {
		assertEquals("e=\\ lead", convert("""{"e":" lead"}""", Format.JSON, Format.PROPERTIES))
		assertTrue(error("""{"a":{}}""", Format.JSON, Format.PROPERTIES).contains("'a'"))
		assertTrue(error("""{"a":[]}""", Format.JSON, Format.PROPERTIES).contains("'a'"))
		assertTrue(error("""{"a.b":1,"a":{"b":2}}""", Format.JSON, Format.PROPERTIES).contains("'a.b'"))
	}

	@Test
	fun `infinity and NaN are refused`() {
		assertEquals("Infinity and NaN aren't supported", error("x: .inf", Format.YAML))
		assertEquals("Infinity and NaN aren't supported", error("x = nan", Format.TOML))
	}
}
