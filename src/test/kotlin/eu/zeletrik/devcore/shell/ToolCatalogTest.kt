package eu.zeletrik.devcore.shell

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@ExtendWith(OutputCaptureExtension::class)
class ToolCatalogTest {
	private fun catalog(hidden: List<String> = emptyList(), defaults: List<String> = emptyList()) =
		ToolCatalog(DevCoreProperties(DevCoreProperties.Tools(hidden), DevCoreProperties.FavoriteDefaults(defaults)))

	@Test
	fun `hidden ids are trimmed, blanks skipped, and each unknown id warned about once`(output: CapturedOutput) {
		val catalog = catalog(hidden = listOf(" base64 ", "", "nope", "nope"))
		assertFalse(catalog.isVisible(Tool.BASE64))
		assertNull(catalog.byId("base64"))
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
		val catalog = catalog()
		assertEquals(Tool.sorted(), catalog.visible)
		assertEquals(emptySet(), catalog.defaultFavorites)
	}
}
