package eu.zeletrik.devcore.tools.hash

import kotlin.test.Test
import kotlin.test.assertEquals

class HmacTest {
	@Test
	fun `RFC 4231 and RFC 2202 vectors`() {
		val text = "what do ya want for nothing?"
		assertEquals("750c783e6ab0b503eaa86e310a5db738", Hashes.hmac(text, "Jefe", "MD5"))
		assertEquals("effcdf6ae5eb2fa2d27416d5f184df9c259a7c79", Hashes.hmac(text, "Jefe", "SHA-1"))
		assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", Hashes.hmac(text, "Jefe", "SHA-256"))
		assertEquals(
			"af45d2e376484031617f78d2b58a6b1b9c7ef464f5a01b47e42ec3736322445e8e2240ca5e69e2c78b3239ecfab21649",
			Hashes.hmac(text, "Jefe", "SHA-384"),
		)
		assertEquals(
			"164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea250554" +
				"9758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
			Hashes.hmac(text, "Jefe", "SHA-512"),
		)
	}

	@Test
	fun `the key is taken as UTF-8`() {
		assertEquals("65a819dce492d28ac0c87bad4e1189b6b564d6dfcf9318314b1450c33f9e24c3", Hashes.hmac("abc", "clé", "SHA-256"))
	}
}
