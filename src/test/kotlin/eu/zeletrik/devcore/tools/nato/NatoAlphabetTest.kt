package eu.zeletrik.devcore.tools.nato

import kotlin.test.Test
import kotlin.test.assertEquals

class NatoAlphabetTest {
	@Test
	fun spelling() {
		assertEquals("Hotel India / Four Two", NatoAlphabet.spell("Hi 42"))
		assertEquals("Alfa Juliett X-ray", NatoAlphabet.spell("ajx"))
		assertEquals("Alfa / Bravo", NatoAlphabet.spell("a   \nb"))
		assertEquals("Alfa ! é", NatoAlphabet.spell("A!é"))
	}
}
