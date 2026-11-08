package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.textfield.TextField
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The field helpers and the output field, without a UI. */
class FieldsTest {
	@Test
	fun `a number outside its field's range or missing is refused with the range in the message`() {
		val field = numberField("n", 1..10, 1)
		assertEquals(1, boundedInt(field))
		field.value = 11
		assertNull(boundedInt(field))
		assertEquals("Enter a number from 1 to 10", field.errorMessage)
		field.value = null
		assertNull(boundedInt(field))
		assertTrue(field.isInvalid)
	}

	@Test
	fun `a whole number is asked for in words, with the range only when there is one`() {
		val field = TextField().apply { value = "abc" }
		assertNull(boundedLong(field))
		assertEquals("Enter a whole number", field.errorMessage)
		assertNull(boundedLong(field, 1L..5L))
		assertEquals("Enter a whole number from 1 to 5", field.errorMessage)
		field.value = " 3 "
		assertEquals(3L, boundedLong(field, 1L..5L))
		assertFalse(field.isInvalid)
	}

	@Test
	fun `the copy button is enabled only while the output has text, and the output refuses to pass the cap`() {
		val output = OutputField("x")
		assertFalse(output.copy.isEnabled)
		output.value = "hi"
		assertTrue(output.copy.isEnabled)
		output.value = "a".repeat(MAX_OUTPUT_CHARS)
		assertFailsWith<OutputTooLarge> { output.value = "a".repeat(MAX_OUTPUT_CHARS + 1) }
	}

	@Test
	fun `a bounded writer takes exactly the cap and refuses the next character while writing`() {
		val writer = BoundedWriter()
		writer.write("a".repeat(MAX_OUTPUT_CHARS))
		val error = assertFailsWith<OutputTooLarge> { writer.write("a") }
		assertEquals("Output is too large (max 5,000,000 characters)", error.message)
		assertEquals(MAX_OUTPUT_CHARS, writer.toString().length)
	}
}
