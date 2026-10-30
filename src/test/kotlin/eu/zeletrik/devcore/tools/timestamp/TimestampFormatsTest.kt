package eu.zeletrik.devcore.tools.timestamp

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimestampFormatsTest {
	private val budapest = ZoneId.of("Europe/Budapest")
	private val utc = ZoneId.of("UTC")
	private val instant = Instant.parse("2026-10-29T14:30:00Z")

	@Test
	fun `RFC 1123 and SQL input`() {
		assertEquals(instant, Timestamps.parse("Thu, 29 Oct 2026 14:30:00 GMT", utc))
		assertEquals(Instant.parse("2026-10-29T13:30:00Z"), Timestamps.parse("2026-10-29 14:30:00", budapest))
		assertEquals(Instant.parse("2026-10-29T14:30:00.250Z"), Timestamps.parse("2026-10-29 14:30:00.250", utc))
	}

	@Test
	fun `RFC 1123 and SQL output`() {
		val converted = Timestamps.convert(instant, budapest, instant)
		assertEquals("Thu, 29 Oct 2026 14:30:00 GMT", converted.rfc1123)
		assertEquals("2026-10-29 15:30:00", converted.sql)
	}

	@Test
	fun `custom pattern, and a broken one`() {
		assertEquals("29.10.2026 15:30", Timestamps.custom(instant, budapest, "dd.MM.yyyy HH:mm"))
		assertFailsWith<IllegalArgumentException> { Timestamps.custom(instant, budapest, "HH:mm {") }
	}

	@Test
	fun `the unparseable message names every accepted format`() {
		val e = assertFailsWith<IllegalArgumentException> { Timestamps.parse("yesterday", utc) }
		listOf("Unix seconds", "ISO-8601", "RFC 1123", "SQL").forEach { assertTrue(it in e.message!!, it) }
	}
}
