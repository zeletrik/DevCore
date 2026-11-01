package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.diff.TextDiffView
import eu.zeletrik.devcore.tools.hashing.PasswordHashingView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An invalid option keeps the output only until a main input changes. */
@SpringBootTest
class StaleOutputBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `password hashing keeps the hash until the password changes`() {
		val view = navigate(PasswordHashingView::class.java)
		view.cost.value = 4
		view.input.value = "first"
		val hash = view.output.value
		assertTrue(hash.startsWith("$2b$04$"), hash)
		view.cost.value = 99
		assertTrue(view.cost.isInvalid)
		assertEquals(hash, view.output.value)
		view.input.value = "second"
		assertEquals("", view.output.value)
	}

	@Test
	fun `text diff keeps the diff until a text changes`() {
		val view = navigate(TextDiffView::class.java)
		view.input.value = "a\nb"
		view.changed.value = "a\nc"
		val diff = view.output.value
		assertTrue(diff.contains("+c"))
		view.context.value = 21
		assertEquals(diff, view.output.value)
		view.changed.value = "a\nd"
		assertEquals("", view.output.value)
		assertEquals("", view.status.text)
	}
}
