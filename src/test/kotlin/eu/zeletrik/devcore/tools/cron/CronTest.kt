package eu.zeletrik.devcore.tools.cron

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CronTest {
	private val budapest = ZoneId.of("Europe/Budapest")
	private val now = Instant.parse("2026-10-29T10:00:00Z")

	private fun describe(expression: String, flavour: CronFlavour = CronFlavour.UNIX) =
		CronDescriber.describe(CronFlavours.parse(expression, flavour))

	private fun runs(expression: String, flavour: CronFlavour = CronFlavour.UNIX, at: Instant = now) =
		CronFlavours.nextRuns(CronFlavours.parse(expression, flavour), at, budapest)

	private fun error(expression: String, flavour: CronFlavour = CronFlavour.UNIX) =
		assertFailsWith<IllegalArgumentException> { CronFlavours.parse(expression, flavour) }.message!!

	@Test
	fun `descriptions - the special forms`() {
		assertEquals("At 09:00, Monday through Friday", describe("0 9 * * 1-5"))
		assertEquals("Every 15 minutes", describe("*/15 * * * *"))
		assertEquals("Every minute", describe("* * * * *"))
		assertEquals("At minute 30 of every hour", describe("30 * * * *"))
		assertEquals("At minute 0 of every hour", describe("@hourly"))
		assertEquals("At 09:00:30", describe("30 0 9 * * *", CronFlavour.SPRING))
		assertEquals("At 00:00, on day 1 of the month, in January", describe("0 0 1 1 *"))
		assertEquals("At 00:00, on day 1 of the month or on Monday", describe("0 0 1 * 1"))
		assertEquals("At 10:15, on the third Friday of the month", describe("0 15 10 ? * 6#3", CronFlavour.QUARTZ))
		assertEquals("At 00:00, on the last day of the month", describe("0 0 0 L * ?", CronFlavour.SPRING))
		assertEquals("At 00:00, on the weekday nearest day 15", describe("0 0 0 15W * ?", CronFlavour.SPRING))
		assertEquals("At 00:00, on the last Friday of the month", describe("0 0 0 ? * 5L", CronFlavour.SPRING))
		assertEquals("At 00:00, on day 1 of the month, January through March", describe("0 0 1 1-3 *"))
		assertEquals("At 00:00, on day 1 of the month, every 3 months", describe("0 0 1 */3 *"))
	}

	@Test
	fun `descriptions - fallback phrasing`() {
		assertEquals("Minutes 5 and 10, every 2 hours, hours 9 through 17", describe("5,10 9-17/2 * * *"))
		assertEquals("Minute 0, hours 9 through 17", describe("0 9-17 * * *"))
	}

	@Test
	fun `next runs in the selected zone`() {
		assertEquals(
			listOf("2026-10-30T09:00:00+01:00", "2026-11-02T09:00:00+01:00", "2026-11-03T09:00:00+01:00", "2026-11-04T09:00:00+01:00", "2026-11-05T09:00:00+01:00"),
			runs("0 9 * * 1-5"),
		)
		assertEquals(emptyList(), runs("0 0 30 2 *"))
	}

	@Test
	fun `unix either-day rule, spring both-days rule`() {
		assertEquals(
			listOf("2026-11-01T00:00:00+01:00", "2026-11-02T00:00:00+01:00", "2026-11-09T00:00:00+01:00", "2026-11-16T00:00:00+01:00", "2026-11-23T00:00:00+01:00"),
			runs("0 0 1 * 1"),
		)
		assertEquals(listOf("2027-02-01T00:00:00+01:00", "2027-03-01T00:00:00+01:00"), runs("0 0 0 1 * MON", CronFlavour.SPRING).take(2))
	}

	@Test
	fun `spring seconds field`() {
		assertEquals(
			listOf("2026-10-29T11:05:00+01:00", "2026-10-29T11:10:00+01:00", "2026-10-29T11:15:00+01:00", "2026-10-29T11:20:00+01:00", "2026-10-29T11:25:00+01:00"),
			runs("0 */5 * * * *", CronFlavour.SPRING, Instant.parse("2026-10-29T10:02:30Z")),
		)
	}

	@Test
	fun `sunday as 0 or 7, names, and macros`() {
		listOf("0 0 * * 0", "0 0 * * 7").forEach { expression ->
			assertTrue(runs(expression).all { ZonedDateTime.parse(it).dayOfWeek.value == 7 }, expression)
		}
		assertEquals(runs("0 9 * 1-3 1-5"), runs("0 9 * JAN-MAR MON-FRI"))
		mapOf(
			"@yearly" to "0 0 1 1 *", "@annually" to "0 0 1 1 *", "@monthly" to "0 0 1 * *", "@weekly" to "0 0 * * 0",
			"@daily" to "0 0 * * *", "@midnight" to "0 0 * * *", "@hourly" to "0 * * * *",
		).forEach { (macro, plain) -> assertEquals(runs(plain), runs(macro), macro) }
	}

	@Test
	fun `quartz numbering, year field and the question-mark rule`() {
		assertEquals(
			listOf("2026-11-02T12:00:00+01:00", "2026-11-09T12:00:00+01:00", "2026-11-16T12:00:00+01:00", "2026-11-23T12:00:00+01:00", "2026-11-30T12:00:00+01:00"),
			runs("0 0 12 ? * 2", CronFlavour.QUARTZ),
		)
		assertEquals(runs("0 0 12 ? * MON", CronFlavour.QUARTZ), runs("0 0 12 ? * MON *", CronFlavour.QUARTZ))
		assertEquals("The year field isn't supported", error("0 0 12 * * ? 2030", CronFlavour.QUARTZ))
		listOf("0 0 12 * * MON", "0 0 12 ? * ?").forEach { assertTrue(error(it, CronFlavour.QUARTZ).contains("'?'"), it) }
	}

	@Test
	fun `invalid expressions and reboot`() {
		assertTrue(error("61 * * * *").isNotBlank())
		assertTrue(error("0 0 9 * * *").contains("5 fields"))
		assertEquals("@reboot has no schedule", error("@reboot"))
	}
}
