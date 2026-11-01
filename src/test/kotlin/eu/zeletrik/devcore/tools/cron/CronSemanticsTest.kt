package eu.zeletrik.devcore.tools.cron

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Day-of-week steps, Quartz `L`, `#` ordinals, the either-day rule, phrasing and Unix DST behavior. */
class CronSemanticsTest {
	private val utc = ZoneId.of("UTC")
	private val berlin = ZoneId.of("Europe/Berlin")

	private fun runs(expression: String, flavor: CronFlavor, now: String, zone: ZoneId = utc) =
		CronFlavors.nextRuns(CronFlavors.parse(expression, flavor), Instant.parse(now), zone)

	private fun dates(expression: String, flavor: CronFlavor, now: String = "2026-10-29T10:00:00Z") =
		runs(expression, flavor, now).map { it.substring(0, 10) }

	private fun describe(expression: String, flavor: CronFlavor = CronFlavor.UNIX) =
		CronDescriber.describe(CronFlavors.parse(expression, flavor))

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
		val weekdays = runs("0 0 12 ? * */3", CronFlavor.QUARTZ, "2026-10-29T10:00:00Z")
			.map { java.time.ZonedDateTime.parse(it).dayOfWeek.name }.toSet()
		assertEquals(setOf("SUNDAY", "WEDNESDAY", "SATURDAY"), weekdays)
	}

	@Test
	fun `quartz bare L is Saturday`() {
		val weekdays = runs(
			"0 0 12 ? * L",
			CronFlavor.QUARTZ,
			"2026-10-29T10:00:00Z",
		).map { java.time.ZonedDateTime.parse(it).dayOfWeek.name }
		assertTrue(weekdays.all { it == "SATURDAY" }, weekdays.toString())
		assertEquals("At 12:00, on Saturday", describe("0 0 12 ? * L", CronFlavor.QUARTZ))
	}

	@Test
	fun `ordinals one to five`() {
		assertEquals("2026-11-30", dates("0 0 0 ? * MON#5", CronFlavor.SPRING).first())
		listOf("0 0 0 ? * MON#6", "0 0 0 ? * MON#0").forEach {
			assertEquals(
				"The '#' ordinal must be 1 to 5",
				assertFailsWith<IllegalArgumentException> { CronFlavors.parse(it, CronFlavor.SPRING) }.message,
			)
		}
		assertEquals(
			"The '#' ordinal must be 1 to 5",
			assertFailsWith<IllegalArgumentException> { CronFlavors.parse("0 0 12 ? * 2#6", CronFlavor.QUARTZ) }.message,
		)
	}

	@Test
	fun `either-day only when neither day field starts with a wildcard`() =
		assertEquals(
			listOf("2026-11-09", "2026-11-23", "2026-12-07", "2026-12-21", "2027-01-11"),
			dates("0 0 */2 * 1", CronFlavor.UNIX),
		)

	@Test
	fun `phrasing of steps, lists and special days`() {
		assertEquals("Every minute", describe("*/1 * * * *"))
		assertEquals("At minute 0 of every hour", describe("0 */1 * * *"))
		assertEquals("At 00:00, on Sunday, Tuesday, Thursday and Saturday", describe("0 0 * * */2"))
		assertEquals("At 00:00, on Monday, Wednesday and Friday", describe("0 0 * * 1-5/2"))
		assertEquals("At 00:00, on day 1 of the month, in January, April, July and October", describe("0 0 1 */3 *"))
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
	fun `unix fixed-time job in a DST gap runs when the gap ends`() =
		assertTrue("2026-03-29T03:00:00+02:00" in runs("30 2 * * *", CronFlavor.UNIX, "2026-03-28T12:00:00Z", berlin))

	@Test
	fun `unix fixed-time job in a DST overlap runs once`() {
		val onTheDay = runs(
			"30 2 * * *",
			CronFlavor.UNIX,
			"2026-10-24T12:00:00Z",
			berlin,
		).filter { it.startsWith("2026-10-25") }
		assertEquals(listOf("2026-10-25T02:30:00+02:00"), onTheDay)
	}

	@Test
	fun `unix wildcard job across a DST gap follows the clock`() {
		val next = runs("*/30 * * * *", CronFlavor.UNIX, "2026-03-29T00:20:00Z", berlin)
		assertEquals(listOf("2026-03-29T01:30:00+01:00", "2026-03-29T03:00:00+02:00"), next.take(2))
	}

	@Test
	fun `spring flavor keeps Spring's DST behavior`() =
		assertTrue(
			runs("0 30 2 * * *", CronFlavor.SPRING, "2026-03-28T12:00:00Z", berlin).none { it.startsWith("2026-03-29") }
		)
}
