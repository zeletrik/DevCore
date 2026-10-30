package eu.zeletrik.devcore.tools.morse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MorseCodeTest {
	private fun fails(message: String, block: () -> Unit) = assertEquals(message, assertFailsWith<IllegalArgumentException>(block = block).message)

	@Test
	fun encode() {
		assertEquals("... --- ... -.-.--", MorseCode.encode("SOS!"))
		assertEquals(".... .. / - .... . .-. .", MorseCode.encode("hi  there"))
		assertEquals("..... .--.-. -...- .-.-.", MorseCode.encode("5@=+"))
		fails("No Morse code for '#'") { MorseCode.encode("a#b") }
	}

	@Test
	fun decode() {
		assertEquals("HI THERE", MorseCode.decode(".... ..\n/ - .... . .-. ."))
		fails("Unknown Morse code '......'") { MorseCode.decode("......") }
		fails("Morse code may contain only '.', '-', '/' and spaces") { MorseCode.decode(".x") }
	}
}
