package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.Html
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.html.Pre
import com.vaadin.flow.router.RouterLink
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class AboutBrowserlessTest : SpringBrowserlessTest() {
	private fun bundled(name: String) = javaClass.classLoader.getResource("META-INF/devcore/$name")?.readText()

	@Test
	fun `the jar carries the license, notices, Apache text and dependency list`() {
		assertTrue(bundled("LICENSE").orEmpty().startsWith("MIT License"))
		val notice = assertNotNull(bundled("NOTICE"))
		assertTrue("EFF Large Wordlist" in notice && "CC BY 3.0 US" in notice && "color-name" in notice)
		assertTrue("\n=== " in notice, "upstream NOTICE texts are appended")
		assertTrue(bundled("Apache-2.0.txt").orEmpty().trimStart().startsWith("Apache License"))
		val dependencies = JsonMapper().readTree(bundled("dependencies.json")).path("dependencies").toList()
		assertTrue(dependencies.size > 50)
		assertTrue(dependencies.all { it.path("moduleVersion").asString().isNotEmpty() })
	}

	@Test
	fun `a local build has no changelog`() = assertNull(bundled("CHANGELOG.md"))

	@Test
	fun `a release build bundles the changelog next to the notices`() {
		val bundling = File("build.gradle.kts").readText().substringAfter("tasks.processResources {").substringBefore("\n}")
		assertTrue("into(\"META-INF/devcore\")" in bundling && "from(\"CHANGELOG.md\")" in bundling, bundling)
	}

	@Test
	fun `about shows version, build time, notes, licenses and links`() {
		val view = navigate("about", AboutView::class.java)
		assertEquals("About — DevCore", UI.getCurrent().internals.title)
		assertTrue(view.version.text.startsWith("Version ") && view.version.text != "Version development", view.version.text)
		assertTrue(view.built.text.startsWith("Built 20"), view.built.text)
		assertEquals("No release notes in this build", (view.whatsNew as Paragraph).text)
		assertTrue(`$view`(Pre::class.java).single().text.startsWith("MIT License"))
		assertTrue(view.dependencies.children.count() > 50)
		assertTrue(view.dependencies.children.toList().all { it.element.text.contains(" — ") })
		assertTrue(
			`$view`(Paragraph::class.java).all()
				.any { "EFF Large Wordlist (CC BY 3.0 US)" in it.text && "color-name (MIT)" in it.text }
		)
		val links = `$view`(Anchor::class.java).all().associate { it.text to it.href }
		assertEquals(Project.REPOSITORY_URL, links["GitHub repository"])
		assertEquals("${Project.REPOSITORY_URL}/releases", links["Releases"])
		assertEquals("${Project.REPOSITORY_URL}/security/policy", links["Security policy"])
	}

	@Test
	fun `release notes are rendered and cleaned`() {
		val notes = AboutView.releaseNotes("# Changelog\n\n## 1.0.0\n\n* first release\n\n<script>alert(1)</script>") as Html
		val html = notes.element.getProperty("innerHTML")
		assertTrue("<h2>1.0.0</h2>" in html && "<li>first release</li>" in html, html)
		assertFalse("<script" in html, html)
	}

	@Test
	fun `about is not a tool`() {
		navigate(HomeView::class.java)
		assertTrue(`$view`(RouterLink::class.java).all().none { it.href == "about" })
		val layout = `$`(MainLayout::class.java).single()
		test(layout.search).setValue("about")
		assertTrue(layout.navTree().none { "About" in it }, layout.navTree().toString())
		val before = layout.recents.list
		navigate("about", AboutView::class.java)
		assertEquals(before, layout.recents.list)
		assertTrue(Tool.entries.none { it.view == AboutView::class.java })
	}
}

@SpringBootTest(properties = ["devcore.tools.hidden=about"])
@ExtendWith(OutputCaptureExtension::class)
class AboutHiddenBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `about can't be hidden`(output: CapturedOutput) {
		assertTrue(navigate("about", AboutView::class.java).version.text.startsWith("Version"))
		assertEquals(Tool.entries.size, ToolCatalog(DevCoreProperties(DevCoreProperties.Tools(listOf("about")))).visible.size)
		assertTrue(
			Regex("Ignoring unknown tool id 'about' in DEVCORE_TOOLS_HIDDEN").containsMatchIn(output.all),
			"warning logged",
		)
	}
}
