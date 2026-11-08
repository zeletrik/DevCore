package eu.zeletrik.devcore.tools.jwt

import eu.zeletrik.devcore.tools.FieldError
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtTest {
	private val header = """{"alg":"HS256","typ":"JWT"}"""
	private val jwtIo = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
		"eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
		"SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

	private fun b64(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
	private fun token(payload: String, signature: String = "c2ln") = "${b64(header)}.${b64(payload)}.$signature"
	private fun status(payload: String, now: Long) = JwtDecoder.decode(token(payload), Instant.ofEpochSecond(now)).status
	private fun error(text: String) =
		assertFailsWith<IllegalArgumentException> { JwtDecoder.decode(text, Instant.EPOCH) }.message!!

	@Test
	fun `header and payload are pretty JSON, signature is kept as given`() {
		val jwt = JwtDecoder.decode(token("""{"sub":"123","n":12345678901234567890}"""), Instant.EPOCH)
		assertEquals("{\n  \"alg\": \"HS256\",\n  \"typ\": \"JWT\"\n}", jwt.header)
		assertEquals("{\n  \"sub\": \"123\",\n  \"n\": 12345678901234567890\n}", jwt.payload)
		assertEquals("c2ln", jwt.signature)
	}

	@Test
	fun `whitespace and a Bearer prefix are ignored`() {
		val plain = JwtDecoder.decode(token("""{"a":1}"""), Instant.EPOCH)
		assertEquals(plain, JwtDecoder.decode("  bEaReR ${token("""{"a":1}""")} \n", Instant.EPOCH))
	}

	@Test
	fun `time claims become ISO-8601 with relative time`() {
		val jwt = JwtDecoder.decode(
			token("""{"exp":1700000000,"iat":1700000000.5}"""),
			Instant.ofEpochSecond(1700000000 - 3 * 86400),
		)
		assertEquals("exp: 2023-11-14T22:13:20Z (in 3 days)\niat: 2023-11-14T22:13:20.500Z (in 3 days)", jwt.times)
	}

	@Test
	fun `expiry boundaries`() {
		assertEquals("Expired", status("""{"exp":100}""", 101))
		assertEquals("Expired", status("""{"exp":100}""", 100))
		assertEquals("Active", status("""{"exp":100}""", 99))
	}

	@Test
	fun `not-before boundaries`() {
		assertEquals("Not yet valid", status("""{"exp":200,"nbf":100}""", 99))
		assertEquals("Active", status("""{"exp":200,"nbf":100}""", 100))
		assertEquals("No expiry", status("""{"nbf":100}""", 100))
		assertEquals("No expiry", status("""{"sub":"x"}""", 100))
	}

	@Test
	fun `time claims that are not usable dates are reported as not readable`() {
		val jwt = JwtDecoder.decode(token("""{"exp":"tomorrow"}"""), Instant.EPOCH)
		assertEquals("Expiry not readable", jwt.status)
		assertEquals("exp: not readable", jwt.times)
		assertTrue(jwt.payload.contains("\"tomorrow\""))
		val tooLarge = JwtDecoder.decode(token("""{"exp":1e30}"""), Instant.EPOCH)
		assertEquals("Expiry not readable", tooLarge.status)
		assertEquals("exp: not readable", tooLarge.times)
		val notBefore = JwtDecoder.decode(token("""{"nbf":"soon"}"""), Instant.EPOCH)
		assertEquals("nbf: not readable", notBefore.times)
		assertEquals("No expiry", notBefore.status)
	}

	@Test
	fun `part count errors`() {
		assertEquals("Encrypted JWTs (JWE) aren't supported", error("a.b.c.d.e"))
		listOf("a", "a.b", "a.b.c.d", "a.b.c.d.e.f").forEach {
			assertEquals("Not a JWT: expected 3 dot-separated parts", error(it))
		}
	}

	@Test
	fun `bad header or payload names the part`() {
		assertTrue(error("!!.${b64("{}")}.x").startsWith("Header"))
		assertTrue(error("${b64(header)}.${b64("[1,2]")}.x").startsWith("Payload"))
		assertTrue(error("${b64(header)}.${b64("not json")}.x").startsWith("Payload"))
		assertTrue(error("${b64(header)}..x").startsWith("Payload"))
	}

	@Test
	fun `empty signature decodes`() {
		assertEquals("", JwtDecoder.decode("${b64("""{"alg":"none"}""")}.${b64("{}")}.", Instant.EPOCH).signature)
	}

	@Test
	fun `checks HS256, HS384 and HS512 signatures`() {
		assertEquals("Signature valid", JwtSigner.check(jwtIo, "your-256-bit-secret"))
		assertEquals("Signature invalid", JwtSigner.check(jwtIo, "wrong"))
		assertEquals("Signature invalid", JwtSigner.check("${b64("""{"alg":"HS256"}""")}.e30", "s"))
		listOf(JwtAlgorithm.HS384, JwtAlgorithm.HS512).forEach { algorithm ->
			assertEquals("Signature valid", JwtSigner.check(JwtSigner.sign("""{"a":1}""", algorithm, "s"), "s"), algorithm.name)
		}
	}

	@Test
	fun `a signature differing only in its spare bits is invalid`() {
		val token = JwtSigner.sign("""{"sub":"1"}""", JwtAlgorithm.HS256, "secret")
		assertEquals("Signature valid", JwtSigner.check(token, "secret"))
		val signature = token.substringAfterLast('.')
		val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
		val last = alphabet.indexOf(signature.last())
		// HS256 is 32 bytes: 43 characters, the last carrying 4 data bits and 2 spare ones.
		val flipped = signature.dropLast(1) + alphabet[last xor 1]
		assertEquals(Base64.getUrlDecoder().decode(signature).toList(), Base64.getUrlDecoder().decode(flipped).toList())
		assertEquals("Signature invalid", JwtSigner.check(token.substringBeforeLast('.') + "." + flipped, "secret"))
	}

	@Test
	fun `other algorithms and unreadable headers get a notice`() {
		val none = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."
		listOf(none, "${b64("""{"alg":"RS256"}""")}.e30.abc", "!!.e30.x", "${b64("{}")}.e30.x").forEach {
			assertEquals(JwtSigner.UNSUPPORTED, JwtSigner.check(it, "s"), it)
		}
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
