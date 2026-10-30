package eu.zeletrik.devcore.tools.chmod

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PermissionsTest {
	private fun symbolic(text: String) = Permissions.symbolic(Permissions.parse(text))

	private fun octal(text: String) = Permissions.octal(Permissions.parse(text))

	@Test
	fun `octal to symbolic, special bits included`() {
		assertEquals("rwxr-xr-x", symbolic("755"))
		assertEquals("755", octal("755"))
		assertEquals(listOf("rwsr-xr-x", "rwsr-xr-x", "rwxrwxrwt"), listOf("4755", "04755", "1777").map(::symbolic))
		assertEquals(listOf("rw-r--r--", "rw-r-Sr--", "rw-r-Sr--"), listOf("644", "2644", "02644").map(::symbolic))
	}

	@Test
	fun `symbolic to octal, with a file-type character or a leading zero`() {
		assertEquals("4644", octal("rwSr--r--"))
		assertEquals(listOf("755", "755", "755"), listOf("0755", "-rwxr-xr-x", "drwxr-xr-x").map(::octal))
	}

	@Test
	fun `anything else is not a permission mode`() {
		listOf("75", "14755", "004755", "123456", "758", "rwxrwxrw", "abc").forEach {
			assertEquals(Permissions.INVALID, assertFailsWith<IllegalArgumentException> { Permissions.parse(it) }.message, it)
		}
	}
}
