package eu.zeletrik.devcore.tools.jwt

import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtDecoderTest {
	private val header = """{"alg":"HS256","typ":"JWT"}"""
	private fun b64(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
	private fun token(payload: String, signature: String = "c2ln") = "${b64(header)}.${b64(payload)}.$signature"
	private fun status(payload: String, now: Long) = JwtDecoder.decode(token(payload), Instant.ofEpochSecond(now)).status
	private fun error(text: String) = assertFailsWith<IllegalArgumentException> { JwtDecoder.decode(text, Instant.EPOCH) }.message!!

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
		val jwt = JwtDecoder.decode(token("""{"exp":1700000000,"iat":1700000000.5}"""), Instant.ofEpochSecond(1700000000 - 3 * 86400))
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
	fun `non-numeric time claims are left alone`() {
		val jwt = JwtDecoder.decode(token("""{"exp":"tomorrow"}"""), Instant.EPOCH)
		assertEquals("No expiry", jwt.status)
		assertEquals("", jwt.times)
		assertTrue(jwt.payload.contains("\"tomorrow\""))
	}

	@Test
	fun `part count errors`() {
		assertEquals("Encrypted JWTs (JWE) are not supported", error("a.b.c.d.e"))
		listOf("a", "a.b", "a.b.c.d", "a.b.c.d.e.f").forEach {
			assertEquals("Not a JWT: expected 3 dot-separated parts", error(it))
		}
	}

	@Test
	fun `bad header or payload names the part`() {
		assertTrue(error("!!.${b64("{}")}.x").startsWith("Header"))
		assertTrue(error("${b64(header)}.${b64("[1,2]")}.x").startsWith("Payload"))
		assertTrue(error("${b64(header)}.${b64("not json")}.x").startsWith("Payload"))
	}

	@Test
	fun `empty signature decodes`() {
		assertEquals("", JwtDecoder.decode("${b64("""{"alg":"none"}""")}.${b64("{}")}.", Instant.EPOCH).signature)
	}
}
