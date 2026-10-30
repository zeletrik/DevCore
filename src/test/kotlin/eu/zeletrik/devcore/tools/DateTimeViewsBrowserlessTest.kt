package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.cron.CronFlavour
import eu.zeletrik.devcore.tools.cron.CronView
import eu.zeletrik.devcore.tools.timestamp.TimestampView
import eu.zeletrik.devcore.tools.timezone.TimeZoneView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
class DateTimeViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `cron explainer defaults to Unix and UTC, and recomputes runs on a zone change`() {
		val view = navigate("cron-explainer?input=0%209%20*%20*%201-5", CronView::class.java)
		assertEquals(CronFlavour.UNIX, view.flavour.value)
		assertEquals("UTC", view.zone.value)
		assertEquals("At 09:00, Monday through Friday", view.description.value)
		val utcRuns = view.runs.value
		assertTrue(utcRuns.lines().all { it.endsWith("Z") })
		test(view.zone).selectItem("Asia/Tokyo")
		assertNotEquals(utcRuns, view.runs.value)
		assertTrue(view.runs.value.lines().all { it.endsWith("+09:00") })
		test(view.input).setValue("@reboot")
		assertEquals("@reboot has no schedule", view.input.errorMessage)
		assertEquals("", view.description.value)
	}

	@Test
	fun `time zone converter defaults to UTC on both sides and Now fills the input`() {
		val view = navigate(TimeZoneView::class.java)
		assertEquals("UTC", view.source.value)
		assertEquals(setOf("UTC"), view.targets.value)
		test(view.now).click()
		assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(view.input.value))
		assertTrue(view.output.value.startsWith("UTC  "))
	}

	@Test
	fun `timestamp - a broken custom format marks only that field and keeps the other outputs`() {
		val view = navigate("timestamp?input=1700000000", TimestampView::class.java)
		test(view.format).setValue("HH:mm {")
		assertTrue(view.format.isInvalid)
		assertEquals("", view.custom.value)
		assertEquals("1700000000", view.seconds.value)
		assertFalse(view.input.isInvalid)
	}
}
