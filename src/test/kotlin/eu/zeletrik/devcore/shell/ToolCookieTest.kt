package eu.zeletrik.devcore.shell

import com.vaadin.flow.internal.CurrentInstance
import com.vaadin.flow.server.VaadinResponse
import com.vaadin.flow.server.VaadinServletResponse
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolCookieTest {
	private val catalog = ToolCatalog(DevCoreProperties())

	@Test
	fun `round trip keeps order`() {
		val tools = listOf(Tool.UUID, Tool.BASE64)
		assertEquals(tools, ToolCookie.parse(ToolCookie.serialize(tools)))
	}

	@Test
	fun `unknown and repeated ids are dropped`() {
		assertEquals(listOf(Tool.BASE64), ToolCookie.parse("no-such-tool.base64.also-gone.base64"))
	}

	@Test
	fun `empty cookie means no tools`() {
		assertEquals(emptyList(), ToolCookie.parse(""))
		assertEquals("", ToolCookie.serialize(emptySet()))
	}

	@Test
	fun `recents keep the last five, most recent first, each once`() {
		val recents = Recents(catalog)
		listOf(Tool.BASE64, Tool.HASH, Tool.JSON, Tool.JWT, Tool.TIMESTAMP).forEach(recents::record)
		assertEquals(listOf(Tool.TIMESTAMP, Tool.JWT, Tool.JSON, Tool.HASH, Tool.BASE64), recents.list)
		recents.record(Tool.HASH)
		assertEquals(listOf(Tool.HASH, Tool.TIMESTAMP, Tool.JWT, Tool.JSON, Tool.BASE64), recents.list)
		recents.record(Tool.URL)
		assertEquals(listOf(Tool.URL, Tool.HASH, Tool.TIMESTAMP, Tool.JWT, Tool.JSON), recents.list)
	}

	@Test
	fun `cookie carries ids only, with the persistence and safety attributes`() {
		val servlet = MockHttpServletResponse()
		CurrentInstance.set(VaadinResponse::class.java, VaadinServletResponse(servlet, null))
		try {
			ToolCookie(Recents.COOKIE).write(listOf(Tool.HASH, Tool.BASE64))
		} finally {
			CurrentInstance.clearAll()
		}
		val cookie = servlet.getCookie(Recents.COOKIE)!!
		assertEquals("hash-generator.base64", cookie.value)
		assertEquals(400 * 24 * 60 * 60, cookie.maxAge)
		assertEquals("/", cookie.path)
		assertEquals(true, cookie.isHttpOnly)
		assertEquals("Lax", cookie.getAttribute("SameSite"))
	}
}
