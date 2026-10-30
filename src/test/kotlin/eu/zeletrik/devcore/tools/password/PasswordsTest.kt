package eu.zeletrik.devcore.tools.password

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordsTest {
	private val all = CharSet.entries.toSet()

	@Test
	fun `every selected set appears, only selected sets are used`() {
		repeat(200) {
			val password = Passwords.password(20, all, excludeAmbiguous = false)
			assertEquals(20, password.length)
			CharSet.entries.forEach { set -> assertTrue(password.any { it in set.chars }, "${set.label} in $password") }
		}
		repeat(50) { assertTrue(Passwords.password(30, setOf(CharSet.DIGITS), false).all { it.isDigit() }) }
		repeat(50) { assertTrue(Passwords.password(30, setOf(CharSet.SYMBOLS), false).all { it in "!#$%&()*+,-./:;<=>?@[]^_{|}~" }) }
	}

	@Test
	fun `length bounds and four characters from four sets`() {
		listOf(4, 50, 128).forEach { assertEquals(it, Passwords.password(it, all, false).length) }
		repeat(100) {
			val four = Passwords.password(4, all, false)
			assertEquals(CharSet.entries.map { set -> 1 }, CharSet.entries.map { set -> four.count { it in set.chars } })
		}
	}

	@Test
	fun `ambiguous characters can be excluded`() {
		repeat(200) { assertTrue(Passwords.password(64, all, excludeAmbiguous = true).none { it in "Il1O0o|" }) }
	}

	@Test
	fun passphrases() {
		assertEquals(7776, Passwords.words.size)
		assertEquals(6, Passwords.passphrase(6, "-", false).split("-").size)
		assertTrue(Passwords.passphrase(6, "-", false).split("-").all { it in Passwords.words })
		assertEquals(3, Passwords.passphrase(3, "_", false).split("_").size)
		assertEquals(20, Passwords.passphrase(20, "_", false).split("_").size)
		assertTrue(Passwords.passphrase(6, " ", true).split(" ").all { it[0].isUpperCase() })
	}

	@Test
	fun `entropy estimates`() {
		assertEquals(129, Passwords.passwordEntropy(20, all, false))
		assertEquals(127, Passwords.passwordEntropy(20, all, true))
		assertEquals(77, Passwords.passphraseEntropy(6))
	}
}
