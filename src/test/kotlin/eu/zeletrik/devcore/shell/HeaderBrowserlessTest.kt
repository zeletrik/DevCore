package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.router.RouterLink
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class HeaderBrowserlessTest : SpringBrowserlessTest() {
	private val layout get() = `$`(MainLayout::class.java).single()

	@Test
	fun `the sidebar header links Home, About and the repository`() {
		navigate(HomeView::class.java)
		assertEquals("DevCore", layout.home.text)
		assertEquals("", layout.home.href)
		assertEquals("about", layout.version.href)
		assertEquals(Project.REPOSITORY_URL, layout.repository.href)
		assertEquals("_blank", layout.repository.element.getAttribute("target"))
		assertEquals("noopener noreferrer", layout.repository.element.getAttribute("rel"))
		assertEquals("DevCore on GitHub", layout.repository.ariaLabel.orElse(""))
	}

	@Test
	fun `home opens with the introduction and search shows its shortcut`() {
		navigate(HomeView::class.java)
		assertEquals(Project.INTRO, `$view`(Paragraph::class.java).first().text)
		assertEquals("Ctrl+K / ⌘K", layout.search.helperText)
	}

	@Test
	fun `the not-found page explains what to do`() {
		navigate("no-such-tool", NotFoundView::class.java)
		assertEquals("Not found", layout.title.text)
		assertEquals("Tool not found", `$view`(H2::class.java).single().text)
		val guidance = `$view`(Paragraph::class.java).single()
		assertEquals("Search with Ctrl+K / ⌘K, or go to Home.", guidance.element.textRecursively)
		assertEquals("", guidance.children.toList().filterIsInstance<RouterLink>().single().href)
	}
}
