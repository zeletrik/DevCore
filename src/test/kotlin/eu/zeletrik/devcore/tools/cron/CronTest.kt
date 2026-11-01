package eu.zeletrik.devcore.tools.cron

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CronTest {
	private val utc = ZoneId.of("UTC")
	private val budapest = ZoneId.of("Europe/Budapest")
	private val berlin = ZoneId.of("Europe/Berlin")
	private val now = Instant.parse("2026-10-29T10:00:00Z")

	private fun describe(expression: String, flavor: CronFlavor = CronFlavor.UNIX) =
		CronDescriber.describe(CronFlavors.parse(expression, flavor))

	private fun runs(
		expression: String,
		flavor: CronFlavor = CronFlavor.UNIX,
		at: Instant = now,
		zone: ZoneId = budapest,
	) = CronFlavors.nextRuns(CronFlavors.parse(expression, flavor), at, zone)

	/** The dates of the next runs, in UTC. */
	private fun dates(expression: String, flavor: CronFlavor) = runs(expression, flavor, zone = utc).map { it.take(10) }

	/** The weekdays of the next runs, in UTC. */
	private fun weekdays(expression: String, flavor: CronFlavor) =
		runs(expression, flavor, zone = utc).map { ZonedDateTime.parse(it).dayOfWeek.name }

	private fun error(expression: String, flavor: CronFlavor = CronFlavor.UNIX) =
		assertFailsWith<IllegalArgumentException> { CronFlavors.parse(expression, flavor) }.message!!

	@Test
	fun `descriptions - the special forms`() {
		assertEquals("At 09:00, Monday through Friday", describe("0 9 * * 1-5"))
		assertEquals("Every 15 minutes", describe("*/15 * * * *"))
		assertEquals("Every minute", describe("* * * * *"))
		assertEquals("At minute 30 of every hour", describe("30 * * * *"))
		assertEquals("At minute 0 of every hour", describe("@hourly"))
		assertEquals("At 09:00:30", describe("30 0 9 * * *", CronFlavor.SPRING))
		assertEquals("At 00:00, on day 1 of the month, in January", describe("0 0 1 1 *"))
		assertEquals("At 00:00, on day 1 of the month or on Monday", describe("0 0 1 * 1"))
		assertEquals("At 10:15, on the third Friday of the month", describe("0 15 10 ? * 6#3", CronFlavor.QUARTZ))
		assertEquals("At 00:00, on the last day of the month", describe("0 0 0 L * ?", CronFlavor.SPRING))
		assertEquals("At 00:00, on the weekday nearest day 15", describe("0 0 0 15W * ?", CronFlavor.SPRING))
		assertEquals("At 00:00, on the last Friday of the month", describe("0 0 0 ? * 5L", CronFlavor.SPRING))
		assertEquals("At 00:00, on day 1 of the month, January through March", describe("0 0 1 1-3 *"))
		assertEquals("At 00:00, on day 1 of the month, in January, April, July and October", describe("0 0 1 */3 *"))
	}

	@Test
	fun `descriptions - fallback phrasing`() {
		assertEquals("Minutes 5 and 10, every 2 hours, hours 9 through 17", describe("5,10 9-17/2 * * *"))
		assertEquals("Minute 0, hours 9 through 17", describe("0 9-17 * * *"))
		assertEquals("Every 10 seconds", describe("*/10 * * * * *", CronFlavor.SPRING))
	}

	@Test
	fun `descriptions - day lists, both day fields, a plain day among ordinals and a stepped month`() {
		assertEquals("At 00:00, on days 1 and 15 of the month", describe("0 0 1,15 * *"))
		assertEquals("At 00:00, on day 1 of the month, on Monday", describe("0 0 0 1 * MON", CronFlavor.SPRING))
		assertEquals(
			"At 00:00, on the first Monday and Friday of the month",
			describe("0 0 0 ? * MON#1,FRI", CronFlavor.SPRING),
		)
		assertEquals("At 00:00, on day 1 of the month, in March, July and November", describe("0 0 1 3/4 *"))
	}

	@Test
	fun `phrasing of steps, lists and special days`() {
		assertEquals("Every minute", describe("*/1 * * * *"))
		assertEquals("At minute 0 of every hour", describe("0 */1 * * *"))
		assertEquals("At 00:00, on Sunday, Tuesday, Thursday and Saturday", describe("0 0 * * */2"))
		assertEquals("At 00:00, on Monday, Wednesday and Friday", describe("0 0 * * 1-5/2"))
		assertEquals("At 12:00, on Monday, Wednesday and Friday", describe("0 0 12 ? * 2-6/2", CronFlavor.QUARTZ))
		assertEquals("At 00:00, on the last weekday of the month", describe("0 0 0 LW * ?", CronFlavor.SPRING))
		assertEquals("At 00:00, 3 days before the last day of the month", describe("0 0 0 L-3 * ?", CronFlavor.SPRING))
		assertEquals(
			"At 00:00, on the first Monday and the second Friday of the month",
			describe("0 0 0 ? * MON#1,FRI#2", CronFlavor.SPRING),
		)
		assertEquals(
			"At 00:00, on the first Sunday and the last Thursday of the month",
			describe("0 0 0 ? * 1#1,5L", CronFlavor.QUARTZ),
		)
		assertEquals("Every minute, hour 9", describe("* 9 * * *"))
		assertEquals("Every minute, hours 9 through 17", describe("* 9-17 * * *"))
	}

	@Test
	fun `next runs in the selected zone`() {
		assertEquals(
			listOf(
				"2026-10-30T09:00:00+01:00",
				"2026-11-02T09:00:00+01:00",
				"2026-11-03T09:00:00+01:00",
				"2026-11-04T09:00:00+01:00",
				"2026-11-05T09:00:00+01:00",
			),
			runs("0 9 * * 1-5"),
		)
		assertEquals(emptyList(), runs("0 0 30 2 *"))
	}

	@Test
	fun `unix either-day rule, spring both-days rule`() {
		assertEquals(
			listOf(
				"2026-11-01T00:00:00+01:00",
				"2026-11-02T00:00:00+01:00",
				"2026-11-09T00:00:00+01:00",
				"2026-11-16T00:00:00+01:00",
				"2026-11-23T00:00:00+01:00",
			),
			runs("0 0 1 * 1"),
		)
		assertEquals(
			listOf("2027-02-01T00:00:00+01:00", "2027-03-01T00:00:00+01:00"),
			runs("0 0 0 1 * MON", CronFlavor.SPRING).take(2),
		)
	}

	@Test
	fun `either-day only when neither day field starts with a wildcard`() =
		assertEquals(
			listOf("2026-11-09", "2026-11-23", "2026-12-07", "2026-12-21", "2027-01-11"),
			dates("0 0 */2 * 1", CronFlavor.UNIX),
		)

	@Test
	fun `unix day-of-week step counts from Sunday`() =
		assertEquals(
			listOf("2026-10-31", "2026-11-01", "2026-11-03", "2026-11-05", "2026-11-07"),
			dates("0 0 * * */2", CronFlavor.UNIX),
		)

	@Test
	fun `quartz day-of-week steps count from Sunday`() {
		assertEquals(
			listOf("2026-10-29", "2026-10-31", "2026-11-01", "2026-11-03", "2026-11-05"),
			dates("0 0 12 ? * */2", CronFlavor.QUARTZ),
		)
		assertEquals(setOf("SUNDAY", "WEDNESDAY", "SATURDAY"), weekdays("0 0 12 ? * */3", CronFlavor.QUARTZ).toSet())
	}

	@Test
	fun `quartz bare L is Saturday`() {
		val weekdays = weekdays("0 0 12 ? * L", CronFlavor.QUARTZ)
		assertTrue(weekdays.all { it == "SATURDAY" }, weekdays.toString())
		assertEquals("At 12:00, on Saturday", describe("0 0 12 ? * L", CronFlavor.QUARTZ))
	}

	@Test
	fun `ordinals one to five`() {
		assertEquals("2026-11-30", dates("0 0 0 ? * MON#5", CronFlavor.SPRING).first())
		listOf("0 0 0 ? * MON#6", "0 0 0 ? * MON#0").forEach {
			assertEquals("The '#' ordinal must be 1 to 5", error(it, CronFlavor.SPRING), it)
		}
		assertEquals("The '#' ordinal must be 1 to 5", error("0 0 12 ? * 2#6", CronFlavor.QUARTZ))
	}

	@Test
	fun `macros in the Unix and Spring flavors`() {
		mapOf(
			"@yearly" to "0 0 1 1 *", "@annually" to "0 0 1 1 *", "@monthly" to "0 0 1 * *", "@weekly" to "0 0 * * 0",
			"@daily" to "0 0 * * *", "@midnight" to "0 0 * * *", "@hourly" to "0 * * * *",
		).forEach { (macro, plain) -> assertEquals(runs(plain), runs(macro), macro) }
		assertEquals("At 00:00", describe("@daily", CronFlavor.SPRING))
	}

	@Test
	fun `quartz numbering, year field and the question-mark rule`() {
		assertEquals(
			listOf(
				"2026-11-02T12:00:00+01:00",
				"2026-11-09T12:00:00+01:00",
				"2026-11-16T12:00:00+01:00",
				"2026-11-23T12:00:00+01:00",
				"2026-11-30T12:00:00+01:00",
			),
			runs("0 0 12 ? * 2", CronFlavor.QUARTZ),
		)
		assertEquals(runs("0 0 12 ? * MON", CronFlavor.QUARTZ), runs("0 0 12 ? * MON *", CronFlavor.QUARTZ))
		assertEquals("The year field isn't supported", error("0 0 12 * * ? 2030", CronFlavor.QUARTZ))
		listOf("0 0 12 * * MON", "0 0 12 ? * ?").forEach {
			val message = "Quartz needs exactly one of day-of-month and day-of-week to be '?'"
			assertEquals(message, error(it, CronFlavor.QUARTZ), it)
		}
	}

	@Test
	fun `field counts and reboot`() {
		assertEquals("A Unix expression has 5 fields: minute hour day-of-month month day-of-week", error("0 0 9 * * *"))
		assertEquals(
			"A Spring expression has 6 fields: second minute hour day-of-month month day-of-week",
			error("0 0 9 * *", CronFlavor.SPRING),
		)
		assertEquals(
			"A Quartz expression has 6 or 7 fields: second minute hour day-of-month month day-of-week [year]",
			error("0 0 12 * *", CronFlavor.QUARTZ),
		)
		assertEquals("@reboot has no schedule", error("@reboot"))
	}

	@Test
	fun `unix fixed-time job in a DST gap runs when the gap ends`() {
		val next = runs("30 2 * * *", at = Instant.parse("2026-03-28T12:00:00Z"), zone = berlin)
		assertTrue("2026-03-29T03:00:00+02:00" in next, next.toString())
	}

	@Test
	fun `unix fixed-time job in a DST overlap runs once`() {
		val onTheDay = runs("30 2 * * *", at = Instant.parse("2026-10-24T12:00:00Z"), zone = berlin)
			.filter { it.startsWith("2026-10-25") }
		assertEquals(listOf("2026-10-25T02:30:00+02:00"), onTheDay)
	}

	@Test
	fun `unix wildcard job across a DST gap follows the clock`() {
		val next = runs("*/30 * * * *", at = Instant.parse("2026-03-29T00:20:00Z"), zone = berlin)
		assertEquals(listOf("2026-03-29T01:30:00+01:00", "2026-03-29T03:00:00+02:00"), next.take(2))
	}

	@Test
	fun `the wall-clock rule applies to the Unix flavor only`() {
		val next = runs("0 30 2 * * *", CronFlavor.SPRING, Instant.parse("2026-03-28T12:00:00Z"), berlin)
		assertTrue(next.none { it.startsWith("2026-03-29") }, next.toString())
	}
}
