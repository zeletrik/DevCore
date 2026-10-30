package eu.zeletrik.devcore.tools.uuid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UlidTest {
	private val crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

	@Test
	fun `26 Crockford characters with the milliseconds in the first 48 bits`() {
		val millis = 1_700_000_000_123L
		val ulid = Uuids.ulid(millis)
		assertEquals(26, ulid.length)
		assertTrue(ulid.all { it in crockford })
		val time = ulid.take(10).fold(0L) { acc, c -> acc * 32 + crockford.indexOf(c) }
		assertEquals(millis, time)
	}

	@Test
	fun `generate gives one ULID per line, ignoring case and hyphen options`() {
		val lines = Uuids.generate(IdVersion.ULID, 5, 0, uppercase = false, hyphens = true).lines()
		assertEquals(5, lines.size)
		assertTrue(lines.all { it.length == 26 && it.all { c -> c in crockford } })
	}
}
