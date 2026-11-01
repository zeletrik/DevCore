package eu.zeletrik.devcore

import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.ToolCatalog
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class DevCoreApplicationTests(@Autowired private val catalog: ToolCatalog) {
	@Test
	fun `the application starts with every tool visible`() {
		assertEquals(Tool.sorted(), catalog.visible)
	}
}
