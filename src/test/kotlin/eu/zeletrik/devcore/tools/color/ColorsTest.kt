package eu.zeletrik.devcore.tools.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorsTest {
	private fun notations(text: String) = Colors.notations(Colors.parse(text))

	@Test
	fun `all notations of one colour`() {
		assertEquals(
			ColorNotations("#990000", "rgb(153, 0, 0)", "hsl(0, 100%, 30%)", "hsv(0, 100%, 60%)", "cmyk(0%, 100%, 100%, 40%)", ""),
			notations("#990000"),
		)
	}

	@Test
	fun `every accepted input notation`() {
		listOf("#900", "#990000ff", "rgb(153, 0, 0)", "rgb(153 0 0)", "rgb(60%, 0%, 0%)", "hsl(0, 100%, 30%)", "hsv(0, 100%, 60%)", "cmyk(0%, 100%, 100%, 40%)")
			.forEach { assertEquals("#990000", notations(it).hex, it) }
	}

	@Test
	fun `CSS names both ways, and alpha`() {
		listOf("Red", "RED", "rgb(255, 0, 0)").forEach { assertEquals("red", notations(it).name, it) }
		assertEquals("#663399", notations("rebeccapurple").hex)
		assertEquals("#99000000", notations("rgba(153, 0, 0, 0)").hex)
	}

	@Test
	fun `channel bounds`() {
		assertEquals("#800000", notations("rgb(128, 0, 0)").hex)
		assertEquals("#ff0000", notations("rgb(255, 0, 0)").hex)
		assertEquals("#000000", notations("rgb(0, 0, 0)").hex)
		listOf("rgb(256, 0, 0)", "rgb(300, 0, 0)", "rgb(-1, 0, 0)", "notacolour").forEach {
			assertEquals(Colors.UNRECOGNISED, assertFailsWith<IllegalArgumentException> { Colors.parse(it) }.message, it)
		}
	}
}
