package eu.zeletrik.devcore.tools.uuid

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidsTest {
	private val hyphenated = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

	private fun one(version: Int, uppercase: Boolean = false, hyphens: Boolean = true) =
		Uuids.generate(if (version == 7) IdVersion.V7 else IdVersion.V4, 1, 1_700_000_000_123, uppercase, hyphens)

	@Test
	fun `v4 has version 4 and the RFC variant`() {
		val uuid = UUID.fromString(one(4))
		assertEquals(4, uuid.version())
		assertEquals(2, uuid.variant())
	}

	@Test
	fun `v7 carries the epoch milliseconds in its first 48 bits`() {
		val uuid = UUID.fromString(one(7))
		assertEquals(7, uuid.version())
		assertEquals(2, uuid.variant())
		assertEquals(1_700_000_000_123, uuid.mostSignificantBits ushr 16)
	}

	@Test
	fun `count boundaries`() {
		listOf(1, 5, 1000).forEach { n ->
			assertEquals(n, Uuids.generate(IdVersion.V4, n, 0, uppercase = false, hyphens = true).lines().size)
		}
	}

	@Test
	fun `formats`() {
		assertTrue(hyphenated.matches(one(4)))
		assertTrue(Regex("[0-9A-F-]{36}").matches(one(4, uppercase = true)))
		assertTrue(Regex("[0-9a-f]{32}").matches(one(7, hyphens = false)))
	}
}
