package eu.zeletrik.devcore

import eu.zeletrik.devcore.shell.ContentSecurityPolicyFilter
import eu.zeletrik.devcore.shell.Project
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val client = HttpClient.newHttpClient()

/** The few HTTP facts that are DevCore's own: its security header, its page metadata and what actuator exposes. */
// vaadin-dev is developmentOnly, so a real servlet in tests must run the production bundle.
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["vaadin.productionMode=true"])
class HttpSurfaceTest(@LocalServerPort private val port: Int) {
	private fun get(path: String): HttpResponse<String> =
		client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(), HttpResponse.BodyHandlers.ofString())

	@Test
	fun `every page carries our policy and head values, and leaves framing to the proxy`() {
		val response = get("/")
		val policy = ContentSecurityPolicyFilter.POLICY
		assertEquals(policy, response.headers().firstValue("Content-Security-Policy").orElse(""))
		assertTrue(response.headers().firstValue("X-Frame-Options").isEmpty)
		assertTrue("connect-src 'self';" in policy && "object-src 'none'" in policy)
		assertFalse("frame-ancestors" in policy)
		assertFalse(Regex("https?://").containsMatchIn(policy))
		val head = Jsoup.parse(response.body()).head()
		assertEquals("icons/favicon.svg", head.selectFirst("link[rel=icon][sizes=any]")?.attr("href"))
		assertEquals(listOf(Project.THEME), head.select("meta[name=theme-color]").map { it.attr("content") })
		assertEquals(Project.DESCRIPTION, head.selectFirst("meta[name=description]")?.attr("content"))
	}

	@Test
	fun `health is the only actuator endpoint, and the rest are 404 rather than Vaadin's bootstrap page`() {
		assertEquals(200, get("/actuator/health").statusCode())
		listOf("/actuator", "/actuator/env", "/actuator/beans", "/actuator/info").forEach {
			val response = get(it)
			assertEquals(404, response.statusCode(), it)
			assertFalse("\"_links\"" in response.body(), it)
		}
	}
}
