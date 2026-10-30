package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.router.RouterLink
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(properties = ["devcore.tools.hidden=base64, ,nope", "devcore.favorites.default=json-formatter,base64"])
class OperatorConfigBrowserlessTest : SpringBrowserlessTest() {
	private val layout get() = `$`(MainLayout::class.java).single()
	private fun navTree() = layout.navTree()

	@Test
	fun `a hidden tool is missing from the sidebar and Home`() {
		navigate(HomeView::class.java)
		assertTrue(navTree().none { it.endsWith(Tool.BASE64.title) })
		val hrefs = `$`(RouterLink::class.java).all().map { it.href }
		assertFalse("base64" in hrefs)
	}

	@Test
	fun `search never finds a hidden tool`() {
		navigate(HomeView::class.java)
		test(layout.search).setValue("b64")
		assertEquals(listOf("Home"), navTree())
		assertTrue(layout.noMatches.isVisible)
	}

	@Test
	fun `a hidden tool's URL shows the not-found page`() {
		navigate("base64", NotFoundView::class.java)
		assertEquals("Tool not found", `$`(H2::class.java).single().text)
	}

	@Test
	fun `a browser without a favorites cookie gets the visible defaults`() {
		navigate(HomeView::class.java)
		assertEquals(listOf("Home", Tool.JSON.title), navTree().take(2))
		val visible = Tool.sorted() - Tool.BASE64
		assertEquals(listOf("Favorites") + categoryHeadings(visible), `$`(H2::class.java).all().map { it.text })
	}
}
