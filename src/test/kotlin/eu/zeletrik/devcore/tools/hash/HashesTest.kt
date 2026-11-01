package eu.zeletrik.devcore.tools.hash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashesTest {
	private val lowercaseHex = Regex("[0-9a-f]+")

	@Test
	fun `every listed algorithm digests and HMACs, upper case on request`() {
		Hashes.ALGORITHMS.forEach { algorithm ->
			assertTrue(Hashes.digest("abc", algorithm).matches(lowercaseHex), algorithm)
			assertTrue(Hashes.hmac("abc", "k", algorithm).matches(lowercaseHex), algorithm)
		}
		assertEquals(Hashes.digest("abc", "MD5").uppercase(), Hashes.digest("abc", "MD5", uppercase = true))
		assertEquals(Hashes.hmac("abc", "k", "MD5").uppercase(), Hashes.hmac("abc", "k", "MD5", uppercase = true))
	}
}
