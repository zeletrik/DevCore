package eu.zeletrik.devcore.tools

import eu.zeletrik.devcore.shell.MAX_OUTPUT_CHARS
import eu.zeletrik.devcore.tools.dataformat.DataFormats
import eu.zeletrik.devcore.tools.dataformat.Format
import eu.zeletrik.devcore.tools.regex.RegexTester
import eu.zeletrik.devcore.tools.xml.XmlFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Outputs stop at the cap while they are written, whichever library writes them. */
class OutputCapTest {
	private val tooLarge = "Output is too large (max 5,000,000 characters)"

	private fun capped(block: () -> String) =
		assertEquals(tooLarge, assertFailsWith<IllegalArgumentException> { block() }.message)

	/** An array nested [depth] deep around [items] zeros: tiny input, deeply indented output. */
	private fun deepJson(depth: Int, items: Int) =
		"[".repeat(depth) + List(items) { "0" }.joinToString(",") + "]".repeat(depth)

	@Test
	fun `yaml output is capped`() = capped { DataFormats.convert(deepJson(450, 30_000), Format.JSON, Format.YAML) }

	@Test
	fun `csv output is capped`() {
		val rows = (1..3_000).joinToString(",", "[", "]") { """{"k$it":1}""" }
		capped { DataFormats.convert(rows, Format.JSON, Format.CSV) }
	}

	@Test
	fun `properties output is capped`() {
		val deep = (1..400).joinToString("") { """{"key$it":""" } + (1..2_000)
			.joinToString(",", "[", "]") { "0" } + "}"
			.repeat(400)
		capped { DataFormats.convert(deep, Format.JSON, Format.PROPERTIES) }
	}

	@Test
	fun `xml output is capped`() {
		val xml = "<a>".repeat(90) + "<b/>".repeat(60_000) + "</a>".repeat(90)
		capped { XmlFormatter.format(xml) }
	}

	@Test
	fun `regex replacement stops at the cap and on the deadline`() {
		val pattern = RegexTester.compile("a", emptySet())
		capped { RegexTester.replace(pattern, "a".repeat(1_000), "x".repeat(5_001), RegexTester.deadline()) }
		assertEquals(
			MAX_OUTPUT_CHARS,
			RegexTester.replace(pattern, "a".repeat(1_000), "x".repeat(5_000), RegexTester.deadline()).length,
		)
		assertFailsWith<RegexTester.Timeout> { RegexTester.replace(pattern, "aaa", "b", System.nanoTime() - 1) }
	}

	@Test
	fun `replacement syntax matches Java's`() {
		val pattern = RegexTester.compile("(?<u>\\w+)@(\\w+)", emptySet())
		assertEquals(
			"x at ann <ann> ${'$'}",
			RegexTester.replace(pattern, "ann@x", "${'$'}2 at ${'$'}1 <${'$'}{u}> \\${'$'}", RegexTester.deadline()),
		)
		assertEquals("ann@x", RegexTester.replace(pattern, "ann@x", "${'$'}0", RegexTester.deadline()))
		assertEquals("x1", RegexTester.replace(pattern, "ann@x", "${'$'}21", RegexTester.deadline()))
		listOf("${'$'}", "${'$'}3", "${'$'}{x}", "\\", "${'$'}a").forEach { bad ->
			assertFailsWith<IllegalArgumentException>(bad) { RegexTester.replace(pattern, "ann@x", bad, RegexTester.deadline()) }
		}
	}
}
