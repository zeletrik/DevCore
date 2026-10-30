package eu.zeletrik.devcore

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val client = HttpClient.newHttpClient()

fun get(port: Int, path: String): HttpResponse<String> =
	client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(), HttpResponse.BodyHandlers.ofString())

// vaadin-dev is developmentOnly, so a real servlet in tests must run the production bundle.
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["vaadin.productionMode=true"])
class HttpSurfaceTest(@LocalServerPort private val port: Int) {
	@Test
	fun `health is UP`() {
		val response = get(port, "/actuator/health")
		assertEquals(200, response.statusCode())
		assertTrue(response.body().contains("\"status\":\"UP\""))
	}

	@Test
	fun `no other actuator endpoint is reachable`() {
		listOf("/actuator", "/actuator/env", "/actuator/beans", "/actuator/info").forEach {
			val response = get(port, it)
			assertEquals(404, response.statusCode(), it)
			assertTrue(!response.body().contains("\"_links\""), it)
		}
	}

	@Test
	fun `tool deep links are served`() {
		assertEquals(200, get(port, "/base64?mode=decode&input=aGk%3D").statusCode())
	}
}

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["vaadin.productionMode=true", "server.servlet.context-path=/devcore"])
class ContextPathTest(@LocalServerPort private val port: Int) {
	@Test
	fun `everything moves under the context path`() {
		assertEquals(200, get(port, "/devcore/actuator/health").statusCode())
		assertEquals(200, get(port, "/devcore/").statusCode())
		assertEquals(200, get(port, "/devcore/base64?input=hi").statusCode())
		assertEquals(404, get(port, "/actuator/health").statusCode())
	}
}
