package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.card.Card
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.html.Pre
import com.vaadin.flow.component.sidenav.SideNavItem
import com.vaadin.flow.router.RouterLink
import eu.zeletrik.devcore.tools.base64.Base64View
import eu.zeletrik.devcore.tools.hash.HashGeneratorView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val REPOSITORY = "https://github.com/zeletrik/devcore"

/** The sidebar as labels: top-level entries as is, grouped tools as "Category/Tool". */
private fun MainLayout.navTree(): List<String> = nav.items.flatMap { item ->
	if (item.items.isEmpty()) listOf(item.label) else item.items.map { "${item.label}/${it.label}" }
}

/** The category groups expected for these tools, in sidebar order. */
private fun groupsOf(tools: List<Tool>): List<String> = Category.sorted().flatMap { category ->
	tools.filter { it.category == category }.map { "${category.title}/${it.title}" }
}

/** Home's category section headings for these tools. */
private fun categoryHeadings(tools: List<Tool>): List<String> =
	Category.sorted().filter { category -> tools.any { it.category == category } }.map { it.title }

/** The shell's happy paths: Home, search, favorites, recents, grouping, header, not found and About. */
@SpringBootTest
class ShellBrowserlessTest : SpringBrowserlessTest() {
	private val layout get() = find(MainLayout::class.java).single()
	private fun sections() = find(H2::class.java).all().map { it.text }
	private fun group(category: Category): SideNavItem = layout.nav.items.single { it.label == category.title }
	private fun pressed() = layout.star.element.getAttribute("aria-pressed")

	@Test
	fun `home lists every tool once per category, with the intro, the sidebar groups and one card shape`() {
		navigate(HomeView::class.java)
		assertEquals("DevCore", UI.getCurrent().internals.title)
		assertEquals(Project.INTRO, findInView(Paragraph::class.java).single().text)
		assertEquals("Ctrl+K / ⌘K", layout.search.helperText)
		val hrefs = findInView(RouterLink::class.java).all().map { it.href }.filter { it.isNotEmpty() }
		val expected = Category.sorted().flatMap { category -> Tool.sorted().filter { it.category == category } }
		assertEquals(expected.map { it.id }, hrefs)
		assertEquals(categoryHeadings(Tool.sorted()), sections())
		assertEquals(listOf("Home") + groupsOf(Tool.sorted()), layout.navTree())
		val card = find(Card::class.java).atIndex(1)
		assertEquals("nowrap", card.title.style.get("white-space"))
		assertEquals("3", card.subtitle.style.get("-webkit-line-clamp"))
		assertEquals("3lh", card.subtitle.style.get("height"))
		val link = findInView(RouterLink::class.java).all().single { it.href == Tool.BASE64.id }
		assertEquals("${Tool.BASE64.title}: ${Tool.BASE64.description}", link.element.getAttribute("title"))
	}

	@Test
	fun `search filters by title or keyword ignoring case, says so without matches, and clearing restores the list`() {
		navigate(HomeView::class.java)
		test(layout.search).setValue("B64")
		assertEquals(listOf("Home", "Encoding/${Tool.BASE64.title}"), layout.navTree())
		test(layout.search).setValue("zzz-no-such-tool")
		assertEquals(listOf("Home"), layout.navTree())
		assertTrue(layout.noMatches.isVisible)
		test(layout.search).setValue("")
		assertEquals(listOf("Home") + groupsOf(Tool.sorted()), layout.navTree())
	}

	@Test
	fun `a tool page opens with its description first, its title in the navbar and the browser title`() {
		val view = navigate(Base64View::class.java)
		assertEquals(Tool.BASE64.title, layout.title.text)
		assertEquals("${Tool.BASE64.title} — DevCore", UI.getCurrent().internals.title)
		assertEquals(Tool.BASE64.description, view.intro.text)
		assertEquals(view.intro, view.children.findFirst().get())
	}

	@Test
	fun `starring lists the tool first and adds a Home section with its card repeated, and unstarring removes both`() {
		navigate(HomeView::class.java)
		assertFalse(layout.star.isVisible)
		navigate(Base64View::class.java)
		navigate(HashGeneratorView::class.java)
		assertTrue(layout.star.isVisible)
		assertEquals("false", pressed())
		assertEquals("Add to favorites", layout.star.ariaLabel.orElse(""))
		test(layout.star).click()
		assertEquals("true", pressed())
		assertEquals("Remove from favorites", layout.star.ariaLabel.orElse(""))
		assertEquals(listOf("Home", Tool.HASH.title) + groupsOf(Tool.sorted()), layout.navTree())
		navigate(HomeView::class.java)
		assertEquals(listOf("Favorites", "Recent") + categoryHeadings(Tool.sorted()), sections())
		val hrefs = findInView(RouterLink::class.java).all().map { it.href }
		assertEquals(3, hrefs.count { it == Tool.HASH.id })
		assertEquals(listOf(Tool.HASH.id, Tool.BASE64.id), hrefs.filter { it.isNotEmpty() }.drop(1).take(2))
		navigate(HashGeneratorView::class.java)
		test(layout.star).click()
		assertEquals("false", pressed())
		assertEquals(listOf("Home") + groupsOf(Tool.sorted()), layout.navTree())
		navigate(HomeView::class.java)
		assertEquals(listOf("Recent") + categoryHeadings(Tool.sorted()), sections())
	}

	@Test
	fun `groups start expanded, a collapsed one survives navigation and a star toggle, and a search expands it`() {
		navigate(HomeView::class.java)
		assertTrue(layout.nav.items.filter { it.items.isNotEmpty() }.all { it.isExpanded })
		group(Category.SECURITY).isExpanded = false
		navigate(HashGeneratorView::class.java)
		assertFalse(group(Category.SECURITY).isExpanded)
		test(layout.star).click()
		assertFalse(group(Category.SECURITY).isExpanded)
		test(layout.search).setValue("hash")
		assertTrue(group(Category.SECURITY).isExpanded)
		assertEquals(listOf("Home", Tool.HASH.title, "Security/${Tool.HASH.title}"), layout.navTree().take(3))
		test(layout.search).setValue("")
		assertTrue(layout.nav.items.filter { it.items.isNotEmpty() }.all { it.isExpanded })
	}

	@Test
	fun `opening a tool, by click or deep link, records it in recents, and Home does not`() {
		navigate(HomeView::class.java)
		assertEquals(emptyList(), layout.recents.list)
		navigate(Base64View::class.java)
		navigate("json-formatter?input=1", Tool.JSON.view)
		navigate(HomeView::class.java)
		assertEquals(listOf(Tool.JSON, Tool.BASE64), layout.recents.list)
	}

	@Test
	fun `the sidebar header links Home, About and the repository`() {
		navigate(HomeView::class.java)
		assertEquals("DevCore", layout.home.text)
		assertEquals("", layout.home.href)
		assertEquals("about", layout.version.href)
		assertEquals(REPOSITORY, layout.repository.href)
		assertEquals("_blank", layout.repository.element.getAttribute("target"))
		assertEquals("noopener noreferrer", layout.repository.element.getAttribute("rel"))
		assertEquals("DevCore on GitHub", layout.repository.ariaLabel.orElse(""))
	}

	@Test
	fun `an unknown path shows the not-found page inside the layout, with guidance`() {
		navigate("no-such-tool", NotFoundView::class.java)
		assertEquals("Not found", layout.title.text)
		assertEquals("Tool not found", findInView(H2::class.java).single().text)
		val guidance = findInView(Paragraph::class.java).single()
		assertEquals("Search with Ctrl+K / ⌘K, or go to Home.", guidance.element.textRecursively)
		assertEquals("", guidance.children.toList().filterIsInstance<RouterLink>().single().href)
		assertFalse(layout.navTree().isEmpty())
	}

	@Test
	fun `about shows the version, build time, licenses and links, and is not a tool`() {
		navigate(HomeView::class.java)
		assertTrue(findInView(RouterLink::class.java).all().none { it.href == "about" })
		test(layout.search).setValue("about")
		assertTrue(layout.navTree().none { "About" in it }, layout.navTree().toString())
		test(layout.search).setValue("")
		val view = navigate("about", AboutView::class.java)
		assertEquals(emptyList(), layout.recents.list)
		assertEquals("About — DevCore", UI.getCurrent().internals.title)
		assertTrue(view.version.text.startsWith("Version "), view.version.text)
		assertTrue(view.built.text.startsWith("Built "), view.built.text)
		assertTrue(findInView(Pre::class.java).single().text.startsWith("MIT License"))
		assertTrue(view.dependencies.children.count() > 50)
		assertTrue(view.dependencies.children.toList().all { it.element.text.contains(" — ") })
		val credits = findInView(Paragraph::class.java).all()
		assertTrue(credits.any { "EFF Large Wordlist (CC BY 3.0 US)" in it.text && "color-name (MIT)" in it.text })
		val links = findInView(Anchor::class.java).all().associate { it.text to it.href }
		assertEquals(REPOSITORY, links["GitHub repository"])
		assertEquals("$REPOSITORY/releases", links["Releases"])
		assertEquals("$REPOSITORY/security/policy", links["Security policy"])
	}
}

/** The operator settings, in one context: a hidden tool and default favorites. */
@SpringBootTest(properties = ["devcore.tools.hidden=base64, ,nope", "devcore.favorites.default=json-formatter,base64"])
class OperatorConfigBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `hidden tools vanish from the sidebar, Home and search, their URL is not found, and defaults skip them`() {
		val layout = find(MainLayout::class.java).single()
		navigate(HomeView::class.java)
		assertTrue(layout.navTree().none { it.endsWith(Tool.BASE64.title) })
		assertFalse("base64" in findInView(RouterLink::class.java).all().map { it.href })
		assertEquals(listOf("Home", Tool.JSON.title), layout.navTree().take(2))
		assertEquals(
			listOf("Favorites") + categoryHeadings(Tool.sorted() - Tool.BASE64),
			find(H2::class.java).all().map { it.text })
		test(layout.search).setValue("b64")
		assertEquals(listOf("Home"), layout.navTree())
		assertTrue(layout.noMatches.isVisible)
		navigate("base64", NotFoundView::class.java)
		assertEquals("Tool not found", find(H2::class.java).single().text)
	}
}
