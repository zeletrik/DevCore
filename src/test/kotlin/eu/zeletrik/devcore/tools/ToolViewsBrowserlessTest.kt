package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.hash.HashGeneratorView
import eu.zeletrik.devcore.tools.json.JsonFormatterView
import eu.zeletrik.devcore.tools.jwt.JwtView
import eu.zeletrik.devcore.tools.timestamp.TimestampView
import eu.zeletrik.devcore.tools.url.UrlEncoderView
import eu.zeletrik.devcore.tools.uuid.UuidGeneratorView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
class ToolViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `json deep link`() {
		val view = navigate("json-formatter?input=%7B%22a%22%3A1%7D", JsonFormatterView::class.java)
		assertEquals("{\n  \"a\": 1\n}", view.output.value)
	}

	@Test
	fun `json - ticking expand re-renders with embedded JSON as structure`() {
		val view = navigate(
			"json-formatter?input=%7B%22m%22%3A%22%7B%5C%22a%5C%22%3A1%7D%22%7D",
			JsonFormatterView::class.java,
		)
		assertTrue(view.output.value.contains("\"m\": \"{"))
		test(view.expand).click()
		assertEquals("{\n  \"m\": {\n    \"a\": 1\n  }\n}", view.output.value)
	}

	@Test
	fun `url deep link with mode`() {
		val view = navigate("url-encoder?mode=decode&input=a%2520b", UrlEncoderView::class.java)
		assertEquals("a b", view.output.value)
	}

	@Test
	fun `hash deep link`() {
		val view = navigate("hash-generator?input=abc", HashGeneratorView::class.java)
		assertEquals("900150983cd24fb0d6963f7d28e17f72", view.outputs.getValue("MD5").value)
	}

	@Test
	fun `jwt deep link shows the not-verified notice`() {
		val token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."
		val view = navigate("jwt?input=$token", JwtView::class.java)
		assertTrue(view.notice.isVisible)
		assertEquals("No expiry", view.status.value)
	}

	@Test
	fun `timestamp defaults to UTC, follows zone changes, and Now fills the input`() {
		val view = navigate("timestamp-converter?input=2026-10-28T10:00:00", TimestampView::class.java)
		assertEquals("UTC", view.zone.value)
		assertEquals("2026-10-28T10:00:00Z", view.utc.value)
		test(view.zone).selectItem("Europe/Budapest")
		assertEquals("2026-10-28T09:00:00Z", view.utc.value)
		test(view.now).click()
		assertTrue(abs(view.input.value.toLong() - Instant.now().epochSecond) < 5)
	}

	@Test
	fun `uuid generator opens with one v4 and regenerates on Generate and option change`() {
		val view = navigate(UuidGeneratorView::class.java)
		val first = view.output.value
		assertEquals(4, UUID.fromString(first).version())
		test(view.generateButton).click()
		assertNotEquals(first, view.output.value)
		test(view.version).selectItem("v7")
		assertEquals(7, UUID.fromString(view.output.value).version())
		test(view.count).setValue(3)
		assertEquals(3, view.output.value.lines().size)
	}

	@Test
	fun `invalid count keeps the previous output`() {
		val view = navigate(UuidGeneratorView::class.java)
		val before = view.output.value
		listOf(0, 1001, null).forEach {
			view.count.value = it
			assertTrue(view.count.isInvalid, "$it")
			assertEquals(before, view.output.value)
		}
	}
}
