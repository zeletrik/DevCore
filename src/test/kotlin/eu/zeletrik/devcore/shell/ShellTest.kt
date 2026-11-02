package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.Html
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.internal.CurrentInstance
import com.vaadin.flow.server.VaadinRequest
import com.vaadin.flow.server.VaadinResponse
import com.vaadin.flow.server.VaadinServletRequest
import com.vaadin.flow.server.VaadinServletResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The shell's own logic: the catalog, the cookies, the release notes and deep work, without a UI. */
@ExtendWith(OutputCaptureExtension::class)
class ShellTest {
	private fun catalog(hidden: List<String> = emptyList(), defaults: List<String> = emptyList()) = ToolCatalog(
		DevCoreProperties(
			DevCoreProperties.Tools(hidden),
			DevCoreProperties.FavoriteDefaults(defaults),
			DevCoreProperties.Repository("https://github.com/zeletrik/devcore"),
		)
	)

	@Test
	fun `hidden ids are trimmed, blanks skipped, and each unknown id warned about once`(output: CapturedOutput) {
		val catalog = catalog(hidden = listOf(" base64 ", "", "nope", "nope"))
		assertFalse(catalog.isVisible(Tool.BASE64))
		assertEquals(Tool.sorted() - Tool.BASE64, catalog.visible)
		assertEquals(1, Regex("Ignoring unknown tool id 'nope' in DEVCORE_TOOLS_HIDDEN").findAll(output.out).count())
	}

	@Test
	fun `default favorites skip hidden and unknown ids`(output: CapturedOutput) {
		val catalog = catalog(hidden = listOf("base64"), defaults = listOf("json-formatter", "base64", "x"))
		assertEquals(setOf(Tool.JSON), catalog.defaultFavorites)
		assertEquals(1, Regex("Ignoring unknown tool id 'x' in DEVCORE_FAVORITES_DEFAULT").findAll(output.out).count())
	}

	@Test
	fun `nothing configured shows every tool and no defaults`() {
		assertEquals(Tool.sorted(), catalog().visible)
		assertEquals(emptySet(), catalog().defaultFavorites)
	}

	@Test
	fun `cookie values round-trip in order, dropping unknown and repeated ids, and empty means none`() {
		val tools = listOf(Tool.UUID, Tool.BASE64)
		assertEquals(tools, ToolCookie.parse(ToolCookie.serialize(tools)))
		assertEquals(listOf(Tool.BASE64), ToolCookie.parse("no-such-tool.base64.also-gone.base64"))
		assertEquals(emptyList(), ToolCookie.parse(""))
		assertEquals("", ToolCookie.serialize(emptySet()))
	}

	@Test
	fun `recents keep the last five, most recent first, each once`() {
		val recents = Recents(catalog())
		listOf(Tool.BASE64, Tool.HASH, Tool.JSON, Tool.JWT, Tool.TIMESTAMP).forEach(recents::record)
		assertEquals(listOf(Tool.TIMESTAMP, Tool.JWT, Tool.JSON, Tool.HASH, Tool.BASE64), recents.list)
		recents.record(Tool.HASH)
		assertEquals(listOf(Tool.HASH, Tool.TIMESTAMP, Tool.JWT, Tool.JSON, Tool.BASE64), recents.list)
		recents.record(Tool.URL)
		assertEquals(listOf(Tool.URL, Tool.HASH, Tool.TIMESTAMP, Tool.JWT, Tool.JSON), recents.list)
	}

	@Test
	fun `cookie carries ids only, under the context path, with the persistence and safety attributes`() {
		val servlet = MockHttpServletResponse()
		CurrentInstance.set(VaadinResponse::class.java, VaadinServletResponse(servlet, null))
		CurrentInstance.set(
			VaadinRequest::class.java,
			VaadinServletRequest(MockHttpServletRequest().apply { contextPath = "/devcore" }, null),
		)
		try {
			ToolCookie(Recents.COOKIE).write(listOf(Tool.HASH, Tool.BASE64))
		} finally {
			CurrentInstance.clearAll()
		}
		val cookie = servlet.getCookie(Recents.COOKIE)!!
		assertEquals("hash-generator.base64", cookie.value)
		assertEquals(400 * 24 * 60 * 60, cookie.maxAge)
		assertEquals("/devcore", cookie.path)
		assertEquals(true, cookie.isHttpOnly)
		assertEquals("Lax", cookie.getAttribute("SameSite"))
	}

	@Test
	fun `release notes are cleaned to formatting and links, and a build without notes says so`() {
		val markdown = "# Changelog\n\n## 1.0.0\n\n* first [release](https://example.com)\n\n<script>alert(1)</script>"
		val html = (AboutView.releaseNotes(markdown) as Html).element.getProperty("innerHTML")
		assertTrue("<h2>1.0.0</h2>" in html && "<li>first <a" in html, html)
		assertTrue("rel=\"noopener noreferrer\"" in html, html)
		assertFalse("<script" in html, html)
		assertEquals("No release notes in this build", (AboutView.releaseNotes(null) as Paragraph).text)
	}

	@Test
	fun `deep work answers with its result, or gives up at the time limit with a readable message`() {
		assertEquals(42, DeepWork.run { 42 })
		val tooSlow = assertFailsWith<DeepWork.TooSlow> { DeepWork.run(50.milliseconds) { Thread.sleep(5_000) } }
		assertEquals("Stopped after 50ms: this input takes too long", tooSlow.message)
	}
}
