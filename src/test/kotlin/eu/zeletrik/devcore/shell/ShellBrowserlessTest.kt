package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.card.Card
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.router.RouterLink
import eu.zeletrik.devcore.tools.base64.Base64View
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class ShellBrowserlessTest : SpringBrowserlessTest() {
	private val layout get() = `$`(MainLayout::class.java).single()
	private fun navTree() = layout.navTree()

	@Test
	fun `home lists every tool as a link, by category then name`() {
		navigate(HomeView::class.java)
		val hrefs = `$view`(RouterLink::class.java).all().map { it.href }.filter { it.isNotEmpty() }
		val expected = Category.sorted().flatMap { category -> Tool.sorted().filter { it.category == category } }
		assertEquals(expected.map { it.id }, hrefs)
		assertEquals(categoryHeadings(Tool.sorted()), `$`(H2::class.java).all().map { it.text })
		assertEquals("DevCore", UI.getCurrent().internals.title)
	}

	@Test
	fun `sidebar starts with Home, then category groups with their tools`() {
		navigate(HomeView::class.java)
		assertEquals(listOf("Home") + groupsOf(Tool.sorted()), navTree())
	}

	@Test
	fun `search filters by name or keyword, ignoring case, and clearing restores the list`() {
		navigate(HomeView::class.java)
		test(layout.search).setValue("B64")
		assertEquals(listOf("Home", "Encoding/${Tool.BASE64.title}"), navTree())
		test(layout.search).setValue("")
		assertEquals(listOf("Home") + groupsOf(Tool.sorted()), navTree())
	}

	@Test
	fun `search without matches says so`() {
		navigate(HomeView::class.java)
		test(layout.search).setValue("zzz-no-such-tool")
		assertEquals(listOf("Home"), navTree())
		assertTrue(layout.noMatches.isVisible)
	}

	@Test
	fun `tool page shows its name in the header and the browser title`() {
		navigate(Base64View::class.java)
		assertEquals(Tool.BASE64.title, layout.title.text)
		assertEquals("${Tool.BASE64.title} — DevCore", UI.getCurrent().internals.title)
	}

	@Test
	fun `every tool page opens with its description`() {
		Tool.entries.forEach { tool ->
			UI.getCurrent().navigate(tool.view)
			val view = currentView as ToolView
			assertEquals(tool.description, view.intro.text, tool.id)
			assertEquals(view.intro, view.children.findFirst().get(), tool.id)
		}
	}

	@Test
	fun `home cards share one shape - one title line, three description lines, full text on hover`() {
		navigate(HomeView::class.java)
		val cards = `$`(Card::class.java).all()
		assertEquals(Tool.entries.size, cards.size)
		cards.forEach { card ->
			assertEquals("nowrap", card.title.style.get("white-space"))
			assertEquals("3", card.subtitle.style.get("-webkit-line-clamp"))
			assertEquals("3lh", card.subtitle.style.get("height"))
		}
		val link = `$view`(RouterLink::class.java).all().single { it.href == Tool.BASE64.id }
		assertEquals("${Tool.BASE64.title}: ${Tool.BASE64.description}", link.element.getAttribute("title"))
	}

	@Test
	fun `unknown path shows the not-found page inside the layout`() {
		navigate("no-such-tool", NotFoundView::class.java)
		assertEquals("Tool not found", `$`(H2::class.java).single().text)
		assertFalse(navTree().isEmpty())
	}
}
