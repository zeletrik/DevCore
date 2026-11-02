package eu.zeletrik.devcore.tools

import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaseMappingTest {
	@Test
	fun `long text maps exactly as the JDK maps it, across piece ends`() {
		// Final sigmas before spaces, expanding letters, and supplementary letters that have a case.
		val words = listOf("ΟΔΟΣ", "İstanbul", "Straße", "ΣΑΣ", "😀", "𐐀𐐁", "ﬀ", "ŉ")
		val text = List(4_000) { words[it % words.size] }.joinToString(" ")
		assertEquals(text.lowercase(), text.lowercaseLinear())
		assertEquals(text.uppercase(), text.uppercaseLinear())
		// No whitespace to end a piece after: it must still never split a surrogate pair (𐐀 lowercases to 𐐨).
		val unbroken = "x" + "𐐀".repeat(3_000) + "İß".repeat(3_000)
		assertEquals(unbroken.lowercase(), unbroken.lowercaseLinear())
		assertEquals(unbroken.uppercase(), unbroken.uppercaseLinear())
	}

	@Test
	fun `a million characters whose case mapping expands take well under a second`() {
		val dotted = "İ".repeat(1_000_000)
		assertTrue(measureNanoTime { assertEquals(2_000_000, dotted.lowercaseLinear().length) } < 1_000_000_000)
		val sharp = "ß".repeat(1_000_000)
		assertTrue(measureNanoTime { assertEquals(2_000_000, sharp.uppercaseLinear().length) } < 1_000_000_000)
	}
}
