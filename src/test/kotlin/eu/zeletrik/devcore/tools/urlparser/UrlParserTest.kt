package eu.zeletrik.devcore.tools.urlparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlParserTest {
	private fun lines(url: String) = UrlParser.describe(url).lines()

	private fun fields(url: String) = lines(url).filter { !it.startsWith("  ") && ": " in it }
		.associate { it.substringBefore(": ") to it.substringAfter(": ") }

	@Test
	fun components() {
		assertEquals(
			mapOf(
				"Scheme" to "https", "Username" to "ann", "Password" to "secret", "Host" to "example.com", "Port" to "8443",
				"Path" to "/p/q", "Query" to "x=1", "Fragment" to "top",
			),
			fields("https://ann:secret@example.com:8443/p/q?x=1#top"),
		)
	}

	@Test
	fun `default ports, password only when present, surrounding whitespace`() {
		mapOf("https" to "443", "http" to "80", "wss" to "443", "ftp" to "21").forEach { (scheme, port) ->
			assertEquals("default ($port)", fields("$scheme://example.com/")["Port"])
		}
		val user = fields("https://ann@example.com/")
		assertEquals("ann", user["Username"])
		assertFalse("Password" in user)
		assertEquals("example.com", fields(" https://example.com/ ")["Host"])
		assertEquals("localhost", fields("http://localhost:8080/")["Host"])
	}

	@Test
	fun `query parameters decoded in order with repeats`() {
		val params = lines("https://h/?a=1&b=x+y&a=2&c&d=%C3%A9").dropWhile { it != "Query parameters:" }.drop(1)
		assertEquals(listOf("  a = 1", "  b = x y", "  a = 2", "  c =", "  d = é"), params)
	}

	@Test
	fun errors() {
		assertEquals(
			"Not an absolute URL (it needs a scheme like https://)",
			assertFailsWith<IllegalArgumentException> { UrlParser.describe("example.com/x") }.message,
		)
		assertTrue(
			assertFailsWith<IllegalArgumentException> {
				UrlParser.describe("https://exa mple.com/")
			}.message!!.startsWith("Not a valid URL: ")
		)
	}

	@Test
	fun `authorities java's URI can't split as a server still show their parts`() {
		mapOf(
			"http://my_service:8080/api" to listOf("Host: my_service", "Port: 8080", "Path: /api"),
			"postgres://user:p%40ss@db_host:5432/mydb" to
				listOf("Username: user", "Password: p@ss", "Host: db_host", "Port: 5432"),
			"https://bücher.de/pfad" to listOf("Host: bücher.de", "Port: default (443)"),
			"https://user%3Aname:pw@example.com/" to listOf("Username: user:name", "Password: pw", "Host: example.com"),
			"http://example.com:80/" to listOf("Port: 80"),
			"https://a+b:c+d@example.com/" to listOf("Username: a+b", "Password: c+d"),
			"http://my_service:abc/" to listOf("Host: my_service:abc", "Port: default (80)"),
		).forEach { (url, expected) ->
			val actual = lines(url)
			expected.forEach { assertTrue(it in actual, "$it in $actual") }
		}
	}

	@Test
	fun `only the parts a URL has are listed`() {
		assertEquals(listOf("Scheme: mailto"), lines("mailto:ann@example.com"))
		assertFalse("Path" in fields("https://example.com"))
	}
}
