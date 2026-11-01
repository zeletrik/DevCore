package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.base64.Base64View
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** TextToolView's shared input handling, exercised through the Base64 tool. */
@SpringBootTest
class TextToolBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `recomputes live on input and immediately on option change`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("???")
		assertEquals("Pz8/", view.output.value)
		test(view.urlSafe).selectItem("URL-safe")
		assertEquals("Pz8_", view.output.value)
	}

	@Test
	fun `empty input gives empty output and no error`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("hi")
		test(view.input).setValue("")
		assertEquals("", view.output.value)
		assertFalse(view.input.isInvalid)
	}

	@Test
	fun `invalid input shows an error and clears the output, valid input recovers`() {
		val view = navigate(Base64View::class.java)
		test(view.mode).selectItem("Decode")
		test(view.input).setValue("aGk=")
		test(view.input).setValue("aGk*")
		assertTrue(view.input.isInvalid)
		assertEquals("Invalid Base64", view.input.errorMessage)
		assertEquals("", view.output.value)
		test(view.input).setValue("aGk=")
		assertFalse(view.input.isInvalid)
		assertEquals("hi", view.output.value)
	}

	@Test
	fun `input cap boundaries`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("a".repeat(999_999))
		assertFalse(view.input.isInvalid)
		test(view.input).setValue("a".repeat(1_000_000))
		assertFalse(view.input.isInvalid)
		assertTrue(view.output.value.isNotEmpty())
		test(view.input).setValue("a".repeat(1_000_001))
		assertTrue(view.input.isInvalid)
		assertEquals("Input is too large (max 1,000,000 characters)", view.input.errorMessage)
		assertEquals("", view.output.value)
	}

	@Test
	fun `copy is disabled while the output is empty`() {
		val view = navigate(Base64View::class.java)
		assertFalse(view.output.copy.isEnabled)
		test(view.input).setValue("hi")
		assertTrue(view.output.copy.isEnabled)
	}

	@Test
	fun `deep link pre-fills input and mode`() {
		val view = navigate("base64?mode=decode&input=aGk%3D", Base64View::class.java)
		assertEquals(Mode.DECODE, view.mode.value)
		assertEquals("aGk=", view.input.value)
		assertEquals("hi", view.output.value)
	}

	@Test
	fun `unknown mode falls back to encode, unknown params are ignored`() {
		val view = navigate("base64?mode=sideways&input=hi&foo=bar", Base64View::class.java)
		assertEquals(Mode.ENCODE, view.mode.value)
		assertEquals("aGk=", view.output.value)
	}

	@Test
	fun `state is kept across navigation within the tab`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("kept")
		navigate(HomeView::class.java)
		val again = navigate(Base64View::class.java)
		assertSame(view, again)
		assertEquals("kept", again.input.value)
	}

	@Test
	fun `a fresh UI starts from defaults`() {
		val view = navigate(Base64View::class.java)
		assertEquals("", view.input.value)
		assertEquals(Mode.ENCODE, view.mode.value)
	}

	@Test
	fun `link values replace kept state`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("kept")
		navigate(HomeView::class.java)
		navigate("base64?mode=decode&input=aGk", Base64View::class.java)
		assertEquals(Mode.DECODE, view.mode.value)
		assertEquals("hi", view.output.value)
	}
}
