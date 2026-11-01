package eu.zeletrik.devcore.tools.numberbase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NumberBasesTest {
	private fun convert(text: String, base: InputBase = InputBase.AUTO, uppercase: Boolean = false) =
		NumberBases.convert(text, base, uppercase)

	private fun error(text: String, base: InputBase) =
		assertFailsWith<IllegalArgumentException> { convert(text, base) }.message

	@Test
	fun `all bases, any size, sign kept`() {
		assertEquals(Bases("11111111", "377", "255", "ff"), convert("255"))
		assertEquals("FF", convert("255", uppercase = true).hex)
		val big = convert("123456789012345678901234567890")
		assertEquals("18ee90ff6c373e0ee4e3f0ad2", big.hex)
		assertEquals("143564417755415637016711617605322", big.octal)
		assertEquals("-101" to "-5", convert("-0b101").let { it.binary to it.decimal })
	}

	@Test
	fun `prefixes and separators`() {
		assertEquals(listOf("31", "15", "5", "10"), listOf("0x1F", "0o17", "0b101", "010").map { convert(it).decimal })
		assertEquals("31", convert("1F", InputBase.HEX).decimal)
		assertEquals("31", convert("0x1F", InputBase.HEX).decimal)
		assertEquals("1000000", convert("1_000 000").decimal)
	}

	@Test
	fun `invalid digits name the digit and the base`() {
		assertEquals("'9' is not a valid octal digit", error("19", InputBase.OCTAL))
		assertEquals("'2' is not a valid binary digit", error("102", InputBase.BINARY))
		assertEquals("'g' is not a valid hexadecimal digit", error("1g", InputBase.HEX))
	}
}
