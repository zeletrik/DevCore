package eu.zeletrik.devcore.tools.uuid

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidsTest {
	private val hyphenated = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
	private val crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
	private val millis = 1_700_000_000_123L

	private fun one(version: IdVersion, uppercase: Boolean = false, hyphens: Boolean = true) =
		Uuids.generate(version, 1, millis, uppercase, hyphens)

	@Test
	fun `v7 carries the epoch milliseconds in its first 48 bits`() {
		val uuid = UUID.fromString(one(IdVersion.V7))
		assertEquals(7, uuid.version())
		assertEquals(2, uuid.variant())
		assertEquals(millis, uuid.mostSignificantBits ushr 16)
	}

	@Test
	fun `a ULID is 26 Crockford characters with the milliseconds in the first 48 bits`() {
		val ulid = Uuids.ulid(millis)
		assertEquals(26, ulid.length)
		assertTrue(ulid.all { it in crockford }, ulid)
		assertEquals(millis, ulid.take(10).fold(0L) { acc, c -> acc * 32 + crockford.indexOf(c) })
	}

	@Test
	fun `count boundaries`() {
		listOf(1, 5, 1000).forEach { n ->
			assertEquals(n, Uuids.generate(IdVersion.V4, n, 0, uppercase = false, hyphens = true).lines().size)
		}
	}

	@Test
	fun `formats`() {
		assertTrue(hyphenated.matches(one(IdVersion.V4)))
		assertTrue(Regex("[0-9A-F-]{36}").matches(one(IdVersion.V4, uppercase = true)))
		assertTrue(Regex("[0-9a-f]{32}").matches(one(IdVersion.V7, hyphens = false)))
	}

	@Test
	fun `ULIDs come one per line, ignoring the case and hyphen options`() {
		val lines = Uuids.generate(IdVersion.ULID, 5, 0, uppercase = false, hyphens = true).lines()
		assertEquals(5, lines.size)
		assertTrue(lines.all { it.length == 26 && it.all { c -> c in crockford } })
	}
}
