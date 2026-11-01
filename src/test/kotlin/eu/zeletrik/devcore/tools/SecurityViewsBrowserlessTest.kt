package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.shell.MAX_INPUT_CHARS
import eu.zeletrik.devcore.shell.Mode
import eu.zeletrik.devcore.shell.NotFoundView
import eu.zeletrik.devcore.tools.basicauth.BasicAuthView
import eu.zeletrik.devcore.tools.hash.HashGeneratorView
import eu.zeletrik.devcore.tools.hashing.HashMode
import eu.zeletrik.devcore.tools.hashing.PasswordHashes
import eu.zeletrik.devcore.tools.hashing.PasswordHashingView
import eu.zeletrik.devcore.tools.jwt.JwtView
import eu.zeletrik.devcore.tools.keys.KeyAlgorithm
import eu.zeletrik.devcore.tools.keys.KeyPairView
import eu.zeletrik.devcore.tools.uuid.IdVersion
import eu.zeletrik.devcore.tools.uuid.UuidGeneratorView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class SecurityViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `password hashing - no password from the URL, mode from the URL, verify shows the result`() {
		val view = navigate("password-hashing?mode=verify&input=hunter2", PasswordHashingView::class.java)
		assertEquals("", view.input.value)
		assertEquals(HashMode.VERIFY, view.mode.value)
		test(view.input).setValue("hunter2")
		test(view.hash).setValue(PasswordHashes.bcrypt("hunter2", 4))
		assertEquals("Match", view.result.text)
		test(view.input).setValue("hunter3")
		assertEquals("No match", view.result.text)
		test(view.hash).setValue("nope")
		assertEquals("Not a bcrypt or Argon2 hash", view.hash.errorMessage)
		assertEquals("", view.result.text)
	}

	@Test
	fun `password hashing - an invalid cost keeps the previous hash`() {
		val view = navigate(PasswordHashingView::class.java)
		test(view.cost).setValue(4)
		test(view.input).setValue("hunter2")
		val before = view.output.value
		assertTrue(before.startsWith("\$2b\$04\$"))
		test(view.cost).setValue(15)
		assertTrue(view.cost.isInvalid)
		assertEquals(before, view.output.value)
	}

	@Test
	fun `key pairs - generated on open, regenerated on Generate and algorithm change, comment edits keep the keys`() {
		val view = navigate(KeyPairView::class.java)
		val first = view.privateKey.value
		assertTrue(view.openSsh.value.startsWith("ssh-ed25519 "))
		test(view.comment).setValue("me@host")
		assertEquals(first, view.privateKey.value)
		assertTrue(view.openSsh.value.endsWith(" me@host"))
		test(view.generateButton).click()
		assertTrue(first != view.privateKey.value)
		test(view.algorithm).selectItem(KeyAlgorithm.RSA_2048.label)
		assertTrue(view.openSsh.value.startsWith("ssh-rsa ") && view.openSsh.value.endsWith(" me@host"))
	}

	private val jwtIo = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
		"eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
		"SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

	@Test
	fun `jwt - decode by default, signature check with a secret, the old id is gone`() {
		val view = navigate("jwt?input=$jwtIo", JwtView::class.java)
		assertEquals(Mode.DECODE, view.mode.value)
		assertTrue(view.notice.text.startsWith("Signature not verified"))
		test(view.secret).setValue("your-256-bit-secret")
		assertEquals("Signature valid", view.notice.text)
		test(view.secret).setValue("wrong")
		assertEquals("Signature invalid", view.notice.text)
		assertTrue(view.header.value.contains("HS256"))
		navigate("jwt-decoder", NotFoundView::class.java)
	}

	@Test
	fun `jwt - encode mode keeps its own text and signs`() {
		val view = navigate("jwt?input=$jwtIo", JwtView::class.java)
		test(view.mode).selectItem("Encode")
		assertEquals("", view.input.value)
		assertEquals("Payload", view.input.label)
		test(view.input).setValue("""{"sub":"1234567890","name":"John Doe","iat":1516239022}""")
		assertEquals("Enter a secret to sign the token", view.secret.errorMessage)
		test(view.secret).setValue("your-256-bit-secret")
		assertEquals(jwtIo, view.token.value)
		test(view.mode).selectItem("Decode")
		assertEquals(jwtIo, view.input.value)
	}

	@Test
	fun `hash - HMAC relabels the outputs, and the key has the input cap`() {
		val view = navigate("hash-generator?input=abc", HashGeneratorView::class.java)
		assertEquals("", view.key.value)
		assertEquals("MD5", view.outputs.getValue("MD5").label)
		test(view.key).setValue("k".repeat(MAX_INPUT_CHARS - 1))
		assertEquals("HMAC-SHA-256", view.outputs.getValue("SHA-256").label)
		assertEquals(64, view.outputs.getValue("SHA-256").value.length)
		test(view.key).setValue("k".repeat(MAX_INPUT_CHARS))
		assertFalse(view.key.isInvalid)
		test(view.key).setValue("k".repeat(MAX_INPUT_CHARS + 1))
		assertEquals("Input is too large (max 1,000,000 characters)", view.key.errorMessage)
		assertEquals("", view.outputs.getValue("SHA-256").value)
	}

	@Test
	fun `uuid - ULID disables the case and hyphen options`() {
		val view = navigate(UuidGeneratorView::class.java)
		test(view.version).selectItem(IdVersion.ULID.label)
		assertFalse(view.uppercase.isEnabled || view.hyphens.isEnabled)
		assertEquals(26, view.output.value.length)
	}

	@Test
	fun `basic auth - nothing comes from the URL, in either mode`() {
		val encode = navigate("basic-auth?input=QWxhZGRpbjpvcGVuIHNlc2FtZQ%3D%3D", BasicAuthView::class.java)
		assertEquals("", encode.input.value)
		assertEquals("", encode.password.value)
		val decode = navigate("basic-auth?mode=decode&input=Basic%20dXNlcjpwYXNz", BasicAuthView::class.java)
		assertEquals(Mode.DECODE, decode.mode.value)
		assertEquals("", decode.input.value)
		assertEquals("", decode.decodedUsername.value)
		assertEquals("", decode.decodedPassword.value)
	}
}
