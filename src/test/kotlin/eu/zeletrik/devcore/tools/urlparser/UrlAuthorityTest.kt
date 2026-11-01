package eu.zeletrik.devcore.tools.urlparser

import kotlin.test.Test
import kotlin.test.assertTrue

/** Authorities that java.net.URI calls "registry-based" still show their parts. */
class UrlAuthorityTest {
	private fun lines(url: String) = UrlParser.describe(url).lines()

	private fun assertHas(url: String, vararg expected: String) {
		val actual = lines(url)
		expected.forEach { assertTrue(it in actual, "$it in $actual") }
	}

	@Test
	fun `host with an underscore`() =
		assertHas("http://my_service:8080/api", "Host: my_service", "Port: 8080", "Path: /api")

	@Test
	fun `userinfo and an underscore host`() =
		assertHas(
			"postgres://user:p%40ss@db_host:5432/mydb",
			"Username: user",
			"Password: p@ss",
			"Host: db_host",
			"Port: 5432",
		)

	@Test
	fun `non-ASCII host`() = assertHas("https://bücher.de/pfad", "Host: bücher.de", "Port: default (443)")

	@Test
	fun `an encoded colon belongs to the username`() =
		assertHas("https://user%3Aname:pw@example.com/", "Username: user:name", "Password: pw", "Host: example.com")

	@Test
	fun `a written default port is shown as written`() = assertHas("http://example.com:80/", "Port: 80")

	@Test
	fun `plus stays a plus in userinfo`() = assertHas("https://a+b:c+d@example.com/", "Username: a+b", "Password: c+d")
}
