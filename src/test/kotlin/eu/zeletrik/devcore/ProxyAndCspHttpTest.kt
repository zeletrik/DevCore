package eu.zeletrik.devcore

import eu.zeletrik.devcore.shell.ContentSecurityPolicyFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test-only endpoints showing what the app sees of the request, behind the same filter chain as Vaadin. */
@RestController
class ForwardedEchoController {
	@GetMapping("/test-echo")
	fun echo(request: HttpServletRequest) = "${request.requestURL} ${request.contextPath}"

	@GetMapping("/test-redirect")
	fun redirect(response: HttpServletResponse) = response.sendRedirect("next")
}

private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

private fun get(port: Int, path: String, vararg headers: Pair<String, String>): HttpResponse<String> {
	val request = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
	headers.forEach { (name, value) -> request.header(name, value) }
	return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
}

@SpringBootTest(
	webEnvironment = RANDOM_PORT,
	properties = ["vaadin.productionMode=true", "vaadin.exclude-urls=/actuator/**,/test-echo,/test-redirect"],
)
class ProxyAndCspHttpTest(@LocalServerPort private val port: Int) {
	private val prefix = "X-Forwarded-Prefix" to "/tools"

	@Test
	fun `every HTML page carries the policy, with self-only sources, no plugins and no framing limits`() {
		listOf("/", "/base64", "/no-such-tool").forEach { path ->
			val response = get(port, path)
			assertEquals(
				ContentSecurityPolicyFilter.POLICY,
				response.headers().firstValue("Content-Security-Policy").orElse(""),
				path,
			)
			assertTrue(response.headers().firstValue("X-Frame-Options").isEmpty, path)
		}
		val policy = ContentSecurityPolicyFilter.POLICY
		assertTrue("connect-src 'self';" in policy && "object-src 'none'" in policy)
		assertFalse("frame-ancestors" in policy)
		assertFalse(Regex("https?://").containsMatchIn(policy))
	}

	@Test
	fun `behind a path-stripping proxy the bootstrap only uses relative URLs, so assets resolve under the prefix`() {
		val page = get(port, "/", prefix)
		assertEquals(200, page.statusCode())
		assertTrue("<base href=\".\">" in page.body())
		val urls = Regex("""(?:src|href)="([^"]+)"""").findAll(page.body()).map { it.groupValues[1] }.toList()
		assertTrue(urls.isNotEmpty())
		assertTrue(urls.none { it.startsWith("/") || it.startsWith("http") }, urls.toString())
		val script = urls.first { it.endsWith(".js") }
		assertEquals(200, get(port, "/$script", prefix).statusCode())
		assertEquals(200, get(port, "/base64?input=hi", prefix).statusCode())
	}

	@Test
	fun `forwarded headers are honored from any client`() {
		val echo = get(port, "/test-echo", prefix, "X-Forwarded-Proto" to "https",
			"X-Forwarded-Host" to "tools.example.com", "X-Forwarded-Port" to "8443").body()
		assertEquals("https://tools.example.com:8443/tools/test-echo /tools", echo)
	}

	@Test
	fun `redirects use the forwarded scheme, host and port`() {
		val response = get(port, "/test-redirect", "X-Forwarded-Proto" to "https",
			"X-Forwarded-Host" to "tools.example.com", "X-Forwarded-Port" to "8443")
		assertEquals(302, response.statusCode())
		assertEquals("https://tools.example.com:8443/next", response.headers().firstValue("Location").orElse(""))
	}
}
