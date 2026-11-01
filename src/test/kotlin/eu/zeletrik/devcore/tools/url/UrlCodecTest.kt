package eu.zeletrik.devcore.tools.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UrlCodecTest {
	@Test
	fun `encodes everything but the unreserved set`() {
		assertEquals("a%20b%26c%3Dd%2F%C3%A9~AZaz09-._", UrlCodec.encode("a b&c=d/é~AZaz09-._"))
	}

	@Test
	fun `decodes escapes and plus`() {
		assertEquals("a b cé", UrlCodec.decode("a%20b+c%C3%A9"))
	}

	@Test
	fun `malformed escapes are rejected`() {
		listOf("%zz", "abc%", "%4").forEach {
			val error = assertFailsWith<IllegalArgumentException> { UrlCodec.decode(it) }
			assertEquals("Malformed percent-encoding", error.message)
		}
	}
}
