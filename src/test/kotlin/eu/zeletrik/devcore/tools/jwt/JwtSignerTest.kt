package eu.zeletrik.devcore.tools.jwt

import eu.zeletrik.devcore.tools.FieldError
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtSignerTest {
	private val jwtIo = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
		"eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
		"SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

	@Test
	fun `checks HS256, HS384 and HS512 signatures`() {
		assertEquals("Signature valid", JwtSigner.check(jwtIo, "your-256-bit-secret"))
		assertEquals("Signature invalid", JwtSigner.check(jwtIo, "wrong"))
		listOf(JwtAlgorithm.HS384, JwtAlgorithm.HS512).forEach { algorithm ->
			assertEquals("Signature valid", JwtSigner.check(JwtSigner.sign("""{"a":1}""", algorithm, "s"), "s"), algorithm.name)
		}
	}

	@Test
	fun `other algorithms get a notice`() {
		val none = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."
		val rs256 = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"RS256"}""".toByteArray()) + ".e30.abc"
		assertEquals(JwtSigner.UNSUPPORTED, JwtSigner.check(none, "s"))
		assertEquals(JwtSigner.UNSUPPORTED, JwtSigner.check(rs256, "s"))
	}

	@Test
	fun `encodes the jwt-io token and keeps big numbers exact`() {
		assertEquals(
			jwtIo,
			JwtSigner.sign(
				"""{"sub":"1234567890","name":"John Doe","iat":1516239022}""",
				JwtAlgorithm.HS256,
				"your-256-bit-secret",
			),
		)
		assertEquals(
			"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJuIjoxMjM0NTY3ODkwMTIzNDU2Nzg5MH0." +
				"rdffju4lA4n5ussR0Fq54M4nsXG82zJygn8owFox5Yc",
			JwtSigner.sign("""{ "n" : 12345678901234567890 }""", JwtAlgorithm.HS256, "s"),
		)
		listOf(JwtAlgorithm.HS384, JwtAlgorithm.HS512).forEach { algorithm ->
			val header = String(Base64.getUrlDecoder().decode(JwtSigner.sign("{}", algorithm, "s").substringBefore('.')))
			assertEquals("""{"alg":"${algorithm.name}","typ":"JWT"}""", header)
		}
	}

	@Test
	fun `encode errors`() {
		assertEquals(
			"Payload must be a JSON object",
			assertFailsWith<IllegalArgumentException> { JwtSigner.sign("[1]", JwtAlgorithm.HS256, "s") }.message,
		)
		assertTrue(
			assertFailsWith<IllegalArgumentException> {
				JwtSigner.sign("""{"a":""", JwtAlgorithm.HS256, "s")
			}.message!!.contains("line 1, column")
		)
		val e = assertFailsWith<FieldError> { JwtSigner.sign("""{"a":1}""", JwtAlgorithm.HS256, "") }
		assertEquals("secret", e.field)
		assertEquals("Enter a secret to sign the token", e.message)
	}
}
