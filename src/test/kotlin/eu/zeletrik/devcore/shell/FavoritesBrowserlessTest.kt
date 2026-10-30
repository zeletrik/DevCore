package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.html.H2
import eu.zeletrik.devcore.tools.base64.Base64View
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class FavoritesBrowserlessTest : SpringBrowserlessTest() {
	private val layout get() = `$`(MainLayout::class.java).single()
	private fun sections() = `$`(H2::class.java).all().map { it.text }
	private fun navTree() = layout.navTree()
	private fun pressed() = layout.star.element.getAttribute("aria-pressed")

	@Test
	fun `starring a tool lists it first and adds a Home section`() {
		navigate(Base64View::class.java)
		assertEquals("false", pressed())
		assertEquals("Add to favorites", layout.star.ariaLabel.orElse(""))
		test(layout.star).click()
		assertEquals("true", pressed())
		assertEquals("Remove from favorites", layout.star.ariaLabel.orElse(""))
		assertEquals(listOf("Home", Tool.BASE64.title) + groupsOf(Tool.sorted()), navTree())
		navigate(HomeView::class.java)
		assertEquals(listOf("Favorites", "Recent") + categoryHeadings(Tool.sorted()), sections())
	}

	@Test
	fun `unstarring removes it again`() {
		navigate(Base64View::class.java)
		test(layout.star).click()
		test(layout.star).click()
		assertEquals("false", pressed())
		assertEquals(listOf("Home") + groupsOf(Tool.sorted()), navTree())
		navigate(HomeView::class.java)
		assertEquals(listOf("Recent") + categoryHeadings(Tool.sorted()), sections())
	}

	@Test
	fun `star is hidden outside tool pages`() {
		navigate(HomeView::class.java)
		assertFalse(layout.star.isVisible)
		navigate(Base64View::class.java)
		assertTrue(layout.star.isVisible)
	}
}
