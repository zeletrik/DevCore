package eu.zeletrik.devcore.tools.lines

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LineToolsTest {
	private fun apply(lines: List<String>, options: LineOptions) = LineTools.apply(lines.joinToString("\n"), options).split("\n")

	@Test
	fun `defaults keep lines as they are, without a trailing newline`() {
		assertEquals("b\na", LineTools.apply("b\na", LineOptions()))
		assertEquals("b\na", LineTools.apply("b\na\n", LineOptions()))
	}

	@Test
	fun `trim, remove empty and dedupe apply in order`() {
		assertEquals(listOf("b", "a", "A"), apply(listOf(" b", "", "a", " b ", "A"), LineOptions(trim = true, removeEmpty = true, removeDuplicates = true)))
		assertEquals(listOf("a", "b"), apply(listOf("b", "a", "B", "a"), LineOptions(removeDuplicates = true, ignoreCase = true, order = Order.ASCENDING)))
	}

	@Test
	fun orders() {
		assertEquals(listOf("A", "C", "b"), apply(listOf("b", "A", "C"), LineOptions(order = Order.ASCENDING)))
		assertEquals(listOf("A", "b", "C"), apply(listOf("b", "A", "C"), LineOptions(order = Order.ASCENDING, ignoreCase = true)))
		assertEquals(listOf("c", "b", "a"), apply(listOf("b", "a", "c"), LineOptions(order = Order.DESCENDING)))
		assertEquals(listOf("item1", "item2", "item10"), apply(listOf("item10", "item2", "item1"), LineOptions(order = Order.NATURAL)))
		assertEquals(listOf("2", "3", "1"), apply(listOf("1", "3", "2"), LineOptions(order = Order.REVERSE)))
		assertEquals(listOf("f", "é"), apply(listOf("é", "f"), LineOptions(order = Order.ASCENDING)))
	}

	@Test
	fun `shuffle keeps every line and draws a new order each time`() {
		val input = (1..20).map { "line $it" }
		val first = LineTools.apply(input.joinToString("\n"), LineOptions(order = Order.SHUFFLE), Random(1)).split("\n")
		val second = LineTools.apply(input.joinToString("\n"), LineOptions(order = Order.SHUFFLE), Random(2)).split("\n")
		assertEquals(input.sorted(), first.sorted())
		assertNotEquals(first, second)
	}
}
