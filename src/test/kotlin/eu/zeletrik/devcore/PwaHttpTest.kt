package eu.zeletrik.devcore

import eu.zeletrik.devcore.shell.Project
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["vaadin.productionMode=true"])
class PwaHttpTest(@LocalServerPort private val port: Int) {
	private fun page(path: String) = Jsoup.parse(get(port, path).body())

	@Test
	fun `every page links the favicon and manifest and carries the theme color and description`() {
		listOf("/", "/base64", "/about", "/no-such-tool").forEach { path ->
			val head = page(path).head()
			assertEquals("icons/favicon.svg", head.selectFirst("link[rel=icon][sizes=any]")?.attr("href"), path)
			assertEquals(listOf(Project.THEME), head.select("meta[name=theme-color]").map { it.attr("content") }, path)
			assertEquals(Project.DESCRIPTION, head.selectFirst("meta[name=description]")?.attr("content"), path)
			assertTrue(head.selectFirst("link[rel=manifest]") != null, path)
		}
	}

	@Test
	fun `the manifest names DevCore and offers 192 and 512 pixel icons`() {
		val href = page("/").head().selectFirst("link[rel=manifest]")!!.attr("href")
		val response = get(port, "/" + href.removePrefix("./").removePrefix("/"))
		assertEquals(200, response.statusCode())
		val manifest = JsonMapper().readTree(response.body())
		assertEquals("DevCore", manifest.path("name").asString())
		assertEquals("DevCore", manifest.path("short_name").asString())
		val sizes = manifest.path("icons").toList().map { it.path("sizes").asString() }
		assertTrue("192x192" in sizes && "512x512" in sizes, sizes.toString())
		manifest.path("icons").toList().filter { it.path("sizes").asString() in setOf("192x192", "512x512") }.forEach {
			assertEquals(200, get(port, "/" + it.path("src").asString().removePrefix("/")).statusCode(), it.toString())
		}
	}

	@Test
	fun `no service worker is registered, and one left by an older version is removed`() {
		val html = get(port, "/").body()
		val scripts = Jsoup.parse(html).select("script[src]").map { get(port, "/" + it.attr("src")).body() }
		(scripts + html).forEach { assertFalse("serviceWorker.register(" in it) }
		assertTrue("registration.unregister()" in html)
	}
}
