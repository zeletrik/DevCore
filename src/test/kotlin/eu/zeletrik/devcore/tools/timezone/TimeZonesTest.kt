package eu.zeletrik.devcore.tools.timezone

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimeZonesTest {
	private val minus = Char(0x2212)

	private fun convert(text: String, source: String, vararg targets: String) =
		TimeZones.convert(text, ZoneId.of(source), targets.map(ZoneId::of))

	@Test
	fun `one line per zone, source first, in selection order`() {
		assertEquals(
			"Europe/Budapest  2026-10-29 14:30  +01:00\nAsia/Tokyo  2026-10-29 22:30  +09:00\nAsia/Kolkata  2026-10-29 19:00  +05:30",
			convert("2026-10-29 14:30", "Europe/Budapest", "Asia/Tokyo", "Asia/Kolkata"),
		)
		assertEquals("UTC  2026-10-29 14:30  +00:00\nAmerica/New_York  2026-10-29 10:30  -04:00", convert("2026-10-29 14:30", "UTC", "America/New_York"))
	}

	@Test
	fun `day markers`() {
		assertEquals("Asia/Tokyo  2026-10-30 07:30  +09:00 (+1 day)", convert("2026-10-29 23:30", "Europe/Budapest", "Asia/Tokyo").lines()[1])
		assertEquals("Europe/Budapest  2026-10-28 21:00  +01:00 (${minus}1 day)", convert("2026-10-29 05:00", "Asia/Tokyo", "Europe/Budapest").lines()[1])
	}

	@Test
	fun `T separator and seconds`() {
		assertEquals("Europe/Budapest  2026-10-29 14:30:15  +01:00", convert("2026-10-29T14:30:15", "Europe/Budapest").lines()[0])
	}

	@Test
	fun `DST gap moves forward with a note, an overlap takes the earlier offset`() {
		assertEquals(
			"Europe/Berlin  2026-03-29 03:30  +02:00 (adjusted: 02:30 doesn't exist in Europe/Berlin on 2026-03-29)",
			convert("2026-03-29 02:30", "Europe/Berlin").lines()[0],
		)
		assertTrue(convert("2026-10-25 02:30", "Europe/Berlin").lines()[0].endsWith("+02:00"))
	}

	@Test
	fun `unparseable input names the expected format`() {
		val e = assertFailsWith<IllegalArgumentException> { convert("29/10/2026 14:30", "UTC") }
		assertTrue(e.message!!.startsWith("Use an ISO-8601 local date-time"))
	}
}
