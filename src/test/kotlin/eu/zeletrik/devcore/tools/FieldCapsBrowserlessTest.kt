package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.hashing.PasswordHashes
import eu.zeletrik.devcore.tools.json.Indent
import eu.zeletrik.devcore.tools.json.JsonFormatter
import eu.zeletrik.devcore.tools.keys.KeyPairView
import eu.zeletrik.devcore.tools.password.PasswordView
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Argon2 parallelism, the passphrase separator, the key comment and Expand's unquoting passes. */
@SpringBootTest
class FieldCapsBrowserlessTest : SpringBrowserlessTest() {
	private val backslash = "\\"

	private fun phc(password: String, m: Int, t: Int, p: Int): String {
		val salt = ByteArray(16) { it.toByte() }
		val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
			.withMemoryAsKB(m).withIterations(t).withParallelism(p).withSalt(salt).build()
		val hash = ByteArray(32).also {
			Argon2BytesGenerator().apply { init(params) }.generateBytes(password.toByteArray(), it)
		}
		val b64 = Base64.getEncoder().withoutPadding()
		val fields = listOf("argon2id", "v=19", "m=$m,t=$t,p=$p", b64.encodeToString(salt), b64.encodeToString(hash))
		return fields.joinToString("$", prefix = "$")
	}

	private fun verifyError(hash: String) = runCatching { PasswordHashes.verify("x", hash) }.exceptionOrNull()?.message

	@Test
	fun `argon2 parallelism within the cap verifies`() {
		assertTrue(PasswordHashes.verify("pw", phc("pw", 1024, 1, 4)))
		assertTrue(PasswordHashes.verify("pw", phc("pw", 128, 1, 16)))
	}

	@Test
	fun `argon2 parallelism beyond the cap is refused before hashing`() {
		val salt = "AAECAwQFBgcICQoLDA0ODw"
		val hash = "A".repeat(43)
		assertEquals(
			"Cost too high to verify here",
			verifyError("${'$'}argon2id${'$'}v=19${'$'}m=65536,t=10,p=17${'$'}$salt${'$'}$hash"),
		)
		assertEquals(
			"Not a supported hash",
			verifyError("${'$'}argon2id${'$'}v=19${'$'}m=65536,t=10,p=0${'$'}$salt${'$'}$hash"),
		)
		assertEquals(
			"Not a supported hash",
			verifyError("${'$'}argon2id${'$'}v=19${'$'}m=127,t=1,p=16${'$'}$salt${'$'}$hash"),
		)
	}

	@Test
	fun `passphrase separator up to 10 characters`() {
		val view = navigate(PasswordView::class.java)
		view.passphrase.value = true
		test(view.separator).setValue("0123456789")
		assertTrue(view.output.value.contains("0123456789"))
		val before = view.output.value
		test(view.separator).setValue("0123456789A")
		assertTrue(view.separator.isInvalid)
		assertEquals("At most 10 characters", view.separator.errorMessage)
		assertEquals(before, view.output.value)
	}

	@Test
	fun `key comment up to 1,000 characters`() {
		val view = navigate(KeyPairView::class.java)
		test(view.comment).setValue("c".repeat(1_000))
		assertTrue(view.openSsh.value.endsWith(" " + "c".repeat(1_000)))
		val (key, line) = view.privateKey.value to view.openSsh.value
		test(view.comment).setValue("c".repeat(1_001))
		assertTrue(view.comment.isInvalid)
		assertEquals("At most 1,000 characters", view.comment.errorMessage)
		assertEquals(key, view.privateKey.value)
		assertEquals(line, view.openSsh.value)
	}

	/** Unquoted text that needs exactly [passes] decoding passes before it is `{"a":1}`. */
	private fun layered(passes: Int): String {
		var text = "{${backslash}u0022a${backslash}u0022:1}"
		repeat(passes - 1) { text = text.replace(backslash, "${backslash}u005c") }
		return text
	}

	private fun expanded(text: String) = JsonFormatter.format(text, Indent.MINIFIED, expand = true)

	@Test
	fun `expand undoes up to 32 layers`() {
		assertEquals("""{"a":1}""", expanded(layered(2)))
		assertEquals("""{"a":1}""", expanded(layered(32)))
	}

	@Test
	fun `expand stops after 32 layers with the input's own error`() {
		val error = runCatching { expanded(layered(33)) }.exceptionOrNull()?.message.orEmpty()
		assertTrue(error.startsWith("Invalid JSON at line 1, column"), error)
		val chain = backslash + "u005c" + "u005c".repeat(10_000) + "u0041"
		val started = System.nanoTime()
		val chainError = runCatching { expanded(chain) }.exceptionOrNull()?.message.orEmpty()
		assertTrue(chainError.startsWith("Invalid JSON at line 1, column"), chainError)
		assertTrue(System.nanoTime() - started < 1_000_000_000)
		assertFalse(error.contains("Couldn't process"))
	}
}
