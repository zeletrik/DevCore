package eu.zeletrik.devcore.tools.color

import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ColorsTest {
	private fun notations(text: String) = Colors.notations(Colors.parse(text))

	private fun invalid(text: String) = assertFailsWith<IllegalArgumentException> { Colors.parse(text) }.message

	@Test
	fun `all notations of one color`() {
		assertEquals(
			ColorNotations(
				"#990000",
				"rgb(153, 0, 0)",
				"hsl(0, 100%, 30%)",
				"hsv(0, 100%, 60%)",
				"cmyk(0%, 100%, 100%, 40%)",
				"",
			),
			notations("#990000"),
		)
	}

	@Test
	fun `every accepted input notation`() {
		listOf(
			"#900",
			"#990000ff",
			"rgb(153, 0, 0)",
			"rgb(153 0 0)",
			"rgb(60%, 0%, 0%)",
			"hsl(0, 100%, 30%)",
			"hsv(0, 100%, 60%)",
			"cmyk(0%, 100%, 100%, 40%)",
		)
			.forEach { assertEquals("#990000", notations(it).hex, it) }
	}

	@Test
	fun `CSS names both ways, and alpha`() {
		listOf("Red", "RED", "rgb(255, 0, 0)").forEach { assertEquals("red", notations(it).name, it) }
		assertEquals("#663399", notations("rebeccapurple").hex)
		assertEquals("#99000000", notations("rgba(153, 0, 0, 0)").hex)
	}

	@Test
	fun `channel bounds and arity`() {
		assertEquals("#800000", notations("rgb(128, 0, 0)").hex)
		assertEquals("#ff0000", notations("rgb(255, 0, 0)").hex)
		assertEquals("#000000", notations("rgb(0, 0, 0)").hex)
		listOf(
			"rgb(256, 0, 0)", "rgb(300, 0, 0)", "rgb(-1, 0, 0)", "notacolour", "rgb(1, 2)", "hsv(0, 0%, 0%, 1)",
			"cmyk(0%, 0%, 0%)", "hsl(0, 101%, 50%)", "rgba(0, 0, 0, 1.5)",
		).forEach { assertEquals(Colors.INVALID, invalid(it), it) }
	}

	@Test
	fun `alpha as a fraction or a percentage, bare and short hex, and NaN`() {
		assertEquals("rgba(255, 0, 0, 0.502)", notations("#ff000080").rgb)
		assertEquals("rgba(255, 0, 0, 0.5)", notations("rgba(255, 0, 0, 0.5)").rgb)
		assertEquals("rgba(255, 0, 0, 0.5)", notations("rgba(255, 0, 0, 50%)").rgb)
		assertEquals("#ff8800", notations("ff8800").hex)
		assertEquals("#aadddd", notations("add").hex)
		assertEquals("tan", notations("tan").name)
		listOf("hsl(NaN, 50%, 50%)", "rgb(Infinity, 0, 0)").forEach { assertEquals(Colors.INVALID, invalid(it), it) }
	}

	@Test
	fun `hue wraps below zero and past a full turn`() {
		assertEquals("#ff0080", notations("hsl(-30, 100%, 50%)").hex)
		assertEquals("#ff8000", notations("hsl(390deg, 100%, 50%)").hex)
	}

	@Test
	fun `green and blue sectors`() {
		assertEquals("hsl(120, 100%, 50%)", notations("#00ff00").hsl)
		assertEquals("hsl(240, 100%, 50%)", notations("#0000ff").hsl)
	}

	@Test
	fun `a long input is invalid at once`() {
		assertTrue(measureNanoTime { assertEquals(Colors.INVALID, invalid("İ".repeat(1_000_000))) } < 1_000_000_000)
	}
}
