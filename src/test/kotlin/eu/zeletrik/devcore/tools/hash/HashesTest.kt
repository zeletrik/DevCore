package eu.zeletrik.devcore.tools.hash

import kotlin.test.Test
import kotlin.test.assertEquals

class HashesTest {
	private val abc = mapOf(
		"MD5" to "900150983cd24fb0d6963f7d28e17f72",
		"SHA-1" to "a9993e364706816aba3e25717850c26c9cd0d89d",
		"SHA-256" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
		"SHA-384" to "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7",
		"SHA-512" to "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
			"2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
	)

	@Test
	fun `FIPS test vectors for abc`() {
		assertEquals(abc.keys.toList(), Hashes.ALGORITHMS)
		abc.forEach { (algorithm, hex) -> assertEquals(hex, Hashes.digest("abc", algorithm)) }
	}

	@Test
	fun `hashes UTF-8 bytes with LF line endings`() {
		assertEquals("edd3a863872a04239eb29ad4bc12fc892b3d4ae57cc7e786a3697816f8e141c2", Hashes.digest("é\n", "SHA-256"))
	}

	@Test
	fun `uppercase hex`() {
		assertEquals(abc.getValue("MD5").uppercase(), Hashes.digest("abc", "MD5", uppercase = true))
	}
}
