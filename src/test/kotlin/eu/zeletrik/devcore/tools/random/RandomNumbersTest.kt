package eu.zeletrik.devcore.tools.random

import eu.zeletrik.devcore.tools.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RandomNumbersTest {
	@Test
	fun `counts and ranges`() {
		assertEquals(listOf(5L), RandomNumbers.generate(5, 5, 1, unique = false))
		assertTrue(RandomNumbers.generate(1, 100, 10, false).let { it.size == 10 && it.all { n -> n in 1..100 } })
		listOf(1, 1000).forEach { assertEquals(it, RandomNumbers.generate(1, 100, it, false).size) }
		assertTrue(RandomNumbers.generate(-1000, 1000, 100, false).all { it in -1000..1000 })
		assertEquals((1L..10).toList(), RandomNumbers.generate(1, 10, 10, unique = true).sorted())
	}

	@Test
	fun `the full 64-bit range and its upper edge do not overflow`() {
		RandomNumbers.generate(Long.MIN_VALUE, Long.MAX_VALUE, 20, false)
		assertTrue(RandomNumbers.generate(1, Long.MAX_VALUE, 20, false).all { it >= 1 })
		assertEquals(Long.MAX_VALUE, RandomNumbers.draw(Long.MAX_VALUE, Long.MAX_VALUE))
		assertTrue((1..2000).map { RandomNumbers.draw(Long.MAX_VALUE - 1, Long.MAX_VALUE) }.toSet() == setOf(Long.MAX_VALUE - 1, Long.MAX_VALUE))
	}

	@Test
	fun `every value in a small range is drawn about equally often`() {
		val counts = (1..60_000).map { RandomNumbers.draw(1, 6) }.groupingBy { it }.eachCount()
		assertEquals((1L..6).toSet(), counts.keys)
		assertTrue(counts.values.all { it in 9_000..11_000 }, counts.toString())
	}

	@Test
	fun errors() {
		assertEquals("Must be at least the minimum", assertFailsWith<FieldError> { RandomNumbers.generate(5, 4, 1, false) }.message)
		val e = assertFailsWith<FieldError> { RandomNumbers.generate(1, 5, 6, unique = true) }
		assertEquals("count", e.field)
		assertEquals("Only 5 unique numbers in this range", e.message)
		assertEquals("Only 3 unique numbers in this range", assertFailsWith<FieldError> { RandomNumbers.generate(8080, 8082, 4, true) }.message)
	}
}
