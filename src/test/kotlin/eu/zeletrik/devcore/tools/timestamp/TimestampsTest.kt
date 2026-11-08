package eu.zeletrik.devcore.tools.timestamp

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimestampsTest {
	private val budapest = ZoneId.of("Europe/Budapest")
	private val instant = Instant.parse("2026-10-29T14:30:00Z")
	private fun at(text: String, zone: ZoneId = UTC) = Timestamps.parse(text, zone).toString()
	private fun failure(text: String) = assertFailsWith<IllegalArgumentException> { Timestamps.parse(text, UTC) }.message

	@Test
	fun `seconds below the cut-off, milliseconds from it`() {
		assertEquals("2023-11-14T22:13:20Z", at("1700000000"))
		assertEquals("5138-11-16T09:46:39Z", at("99999999999"))
		assertEquals("1973-03-03T09:46:40Z", at("100000000000"))
		assertEquals("2023-11-14T22:13:20Z", at("1700000000000"))
	}

	@Test
	fun `negative values and surrounding whitespace`() {
		assertEquals("1969-12-31T23:59:59Z", at("-1"))
		assertEquals("2023-11-14T22:13:20Z", at("  1700000000 \n"))
	}

	@Test
	fun `accepted input forms`() {
		assertEquals("2026-10-28T10:00:00Z", at("2026-10-28T10:00:00Z"))
		assertEquals("2026-10-28T10:00:00Z", at("2026-10-28T12:00:00+02:00"))
		assertEquals("2026-10-28T09:00:00Z", at("2026-10-28T10:00:00", budapest))
		assertEquals("2026-10-27T23:00:00Z", at("2026-10-28", budapest))
		assertEquals("2026-10-29T14:30:00Z", at("Thu, 29 Oct 2026 14:30:00 GMT"))
		assertEquals("2026-10-29T13:30:00Z", at("2026-10-29 14:30:00", budapest))
		assertEquals("2026-10-29T14:30:00.250Z", at("2026-10-29 14:30:00.250"))
	}

	@Test
	fun `basic offsets, minute precision and zone ids`() {
		mapOf(
			"2026-10-28T10:00:00+0200" to "2026-10-28T08:00:00Z",
			"2026-10-28T10:00:00.000+0000" to "2026-10-28T10:00:00Z",
			"2026-10-28T10:00Z" to "2026-10-28T10:00:00Z",
			"2026-10-28T10:00+02:00" to "2026-10-28T08:00:00Z",
			"2026-10-28T10:00:00+02:00[Europe/Budapest]" to "2026-10-28T08:00:00Z",
		).forEach { (input, utcText) -> assertEquals(utcText, at(input), input) }
	}

	@Test
	fun `all representations`() {
		val now = Instant.parse("2023-11-17T22:13:20Z")
		val converted = Timestamps.convert(Timestamps.parse("1700000000", UTC), UTC, now)
		assertEquals(
			Converted(
				1700000000, 1700000000000, "2023-11-14T22:13:20Z", "2023-11-14T22:13:20Z", "3 days ago",
				"Tue, 14 Nov 2023 22:13:20 GMT", "2023-11-14 22:13:20",
			),
			converted,
		)
	}

	@Test
	fun `seconds round toward negative infinity`() {
		val parsed = Timestamps.parse("-100000001500", UTC)
		assertEquals(-100000002, Timestamps.convert(parsed, UTC, Instant.EPOCH).seconds)
	}

	@Test
	fun `custom patterns use English names, a broken pattern is reported`() {
		assertEquals("29.10.2026 15:30", Timestamps.custom(instant, budapest, "dd.MM.yyyy HH:mm"))
		assertEquals("Thursday 29 October 2026 CET", Timestamps.custom(instant, budapest, "EEEE d MMMM yyyy z"))
		assertEquals("Central European Standard Time", Timestamps.custom(instant, budapest, "zzzz"))
		val e = assertFailsWith<IllegalArgumentException> { Timestamps.custom(instant, budapest, "HH:mm {") }
		assertTrue(e.message!!.startsWith("Not a usable pattern: "), e.message)
	}

	@Test
	fun `errors - unreadable input, and instants no representation can hold`() {
		assertEquals("Timestamp out of range", failure("99999999999999999999"))
		assertEquals(Timestamps.FORMATS, failure("yesterday"))
		assertEquals(Timestamps.FORMATS, failure("   "))
		// A seven-digit year has no RFC 1123 form; Instant.MAX has no epoch milliseconds.
		listOf(Timestamps.parse("99999999999999999", UTC), Instant.MAX).forEach { far ->
			val e = assertFailsWith<IllegalArgumentException> { Timestamps.convert(far, UTC, instant) }
			assertEquals("Timestamp out of range", e.message, far.toString())
		}
	}

	@Test
	fun `relative time`() {
		val now = Instant.parse("2026-10-28T10:00:00Z")
		assertEquals("now", relativeTime(now.plusMillis(999), now))
		assertEquals("1 second ago", relativeTime(now.minusSeconds(1), now))
		assertEquals("59 seconds ago", relativeTime(now.minusSeconds(59), now))
		assertEquals("1 minute ago", relativeTime(now.minusSeconds(60), now))
		assertEquals("1 hour ago", relativeTime(now.minus(Duration.ofHours(1)), now))
		assertEquals("in 5 hours", relativeTime(now.plus(Duration.ofHours(5)), now))
		assertEquals("in 1 day", relativeTime(now.plus(Duration.ofDays(1)), now))
		assertEquals("in 3 days", relativeTime(now.plus(Duration.ofDays(3)), now))
		assertEquals("400 days ago", relativeTime(now.minus(Duration.ofDays(400)), now))
	}
}
