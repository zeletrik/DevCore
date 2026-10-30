package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.base64.Base64View
import eu.zeletrik.devcore.tools.hash.HashGeneratorView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class RecentsBrowserlessTest : SpringBrowserlessTest() {
	private val recents get() = `$`(MainLayout::class.java).single().recents

	@Test
	fun `opening tools records them, but Home does not`() {
		navigate(HomeView::class.java)
		assertEquals(emptyList(), recents.list)
		navigate(Base64View::class.java)
		navigate(HashGeneratorView::class.java)
		navigate(HomeView::class.java)
		assertEquals(listOf(Tool.HASH, Tool.BASE64), recents.list)
	}

	@Test
	fun `a deep link counts as opening the tool`() {
		navigate("json-formatter?input=1", Tool.JSON.view)
		assertEquals(listOf(Tool.JSON), recents.list)
	}
}
