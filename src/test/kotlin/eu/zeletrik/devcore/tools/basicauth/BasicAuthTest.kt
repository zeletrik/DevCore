package eu.zeletrik.devcore.tools.basicauth

import eu.zeletrik.devcore.tools.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasicAuthTest {
	@Test
	fun encode() {
		assertEquals("Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", BasicAuth.encode("Aladdin", "open sesame"))
		assertEquals("Authorization: Basic Wm/Dqzpww6Q=", BasicAuth.encode("Zoë", "pä"))
		assertEquals("Authorization: Basic YTpiOmM=", BasicAuth.encode("a", "b:c"))
		assertEquals("A username can't contain ':'", assertFailsWith<FieldError> { BasicAuth.encode("a:b", "x") }.message)
	}

	@Test
	fun decode() {
		listOf(
			"Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
			"Proxy-Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
			"basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
			" QWxhZGRpbjpvcGVuIHNlc2FtZQ== ",
		).forEach {
			assertEquals("Aladdin" to "open sesame", BasicAuth.decode(it))
		}
		assertEquals("a" to "b:c", BasicAuth.decode("YTpiOmM="))
		assertEquals("Invalid Base64", assertFailsWith<IllegalArgumentException> { BasicAuth.decode("Basic !!!") }.message)
		assertEquals(
			"Decoded value has no ':' between username and password",
			assertFailsWith<IllegalArgumentException> { BasicAuth.decode("YWJj") }.message,
		)
	}
}
