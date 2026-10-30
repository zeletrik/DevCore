package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.sidenav.SideNavItem
import com.vaadin.flow.router.RouterLink
import eu.zeletrik.devcore.tools.base64.Base64View
import eu.zeletrik.devcore.tools.hash.HashGeneratorView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class GroupedNavBrowserlessTest : SpringBrowserlessTest() {
	private val layout get() = `$`(MainLayout::class.java).single()
	private fun group(category: Category): SideNavItem = layout.nav.items.single { it.label == category.title }

	@Test
	fun `every group starts expanded`() {
		navigate(HomeView::class.java)
		assertTrue(layout.nav.items.filter { it.items.isNotEmpty() }.all { it.isExpanded })
	}

	@Test
	fun `a collapsed group stays collapsed while navigating`() {
		navigate(HomeView::class.java)
		group(Category.ENCODING).isExpanded = false
		navigate(HashGeneratorView::class.java)
		assertFalse(group(Category.ENCODING).isExpanded)
	}

	@Test
	fun `toggling a star keeps groups as they were`() {
		navigate(HashGeneratorView::class.java)
		group(Category.ENCODING).isExpanded = false
		test(layout.star).click()
		assertFalse(group(Category.ENCODING).isExpanded)
	}

	@Test
	fun `a search shows every group expanded, matches in a collapsed group included`() {
		navigate(HomeView::class.java)
		group(Category.SECURITY).isExpanded = false
		test(layout.search).setValue("hash")
		assertTrue(group(Category.SECURITY).isExpanded)
		val tree = layout.navTree()
		assertTrue("Security/${Tool.HASH.title}" in tree && tree.drop(1).all { it.startsWith("Security/") }, tree.toString())
		test(layout.search).setValue("")
		assertTrue(layout.nav.items.filter { it.items.isNotEmpty() }.all { it.isExpanded })
	}

	@Test
	fun `a favorite is listed among favorites and in its group`() {
		navigate(HashGeneratorView::class.java)
		test(layout.star).click()
		val tree = layout.navTree()
		assertTrue(Tool.HASH.title in tree)
		assertTrue("Security/${Tool.HASH.title}" in tree)
		test(layout.search).setValue("hash")
		val searched = layout.navTree()
		assertEquals(listOf("Home", Tool.HASH.title, "Security/${Tool.HASH.title}"), searched.take(3))
	}

	@Test
	fun `home shows favorites, then recent, then categories, and repeats cards per section`() {
		navigate(Base64View::class.java)
		navigate(HashGeneratorView::class.java)
		test(layout.star).click()
		navigate(HomeView::class.java)
		assertEquals(listOf("Favorites", "Recent") + categoryHeadings(Tool.sorted()), `$`(H2::class.java).all().map { it.text })
		val hrefs = `$`(RouterLink::class.java).all().map { it.href }
		assertEquals(3, hrefs.count { it == Tool.HASH.id })
		assertEquals(listOf(Tool.HASH.id, Tool.BASE64.id), hrefs.filter { it.isNotEmpty() }.drop(1).take(2))
	}
}
