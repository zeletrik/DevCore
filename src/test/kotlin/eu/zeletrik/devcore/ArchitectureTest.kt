package eu.zeletrik.devcore

import com.vaadin.flow.router.ParentLayout
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.AboutView
import eu.zeletrik.devcore.shell.HomeView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.NotFoundView
import eu.zeletrik.devcore.shell.Project
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.ToolView
import tools.jackson.dataformat.yaml.YAMLMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The conventions every tool follows, checked over the registry and the sources rather than tool by tool. */
class ArchitectureTest {
	private val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

	/** Ids are a public contract (deep links, bookmarks, Raycast quicklinks): never change a released one. */
	@Test
	fun `released ids`() {
		assertEquals(
			mapOf(
				Tool.JSON to "json-formatter",
				Tool.BASE64 to "base64",
				Tool.URL to "url-encoder",
				Tool.JWT to "jwt",
				Tool.HASH to "hash-generator",
				Tool.UUID to "uuid-generator",
				Tool.TIMESTAMP to "timestamp-converter",
				Tool.HTML_ENTITIES to "html-entities",
				Tool.UNICODE_ESCAPE to "unicode-escape",
				Tool.STRING_ESCAPE to "string-escape",
				Tool.MORSE to "morse-code",
				Tool.NATO to "nato-alphabet",
				Tool.REGEX to "regex-tester",
				Tool.TEXT_DIFF to "text-diff",
				Tool.CASE_CONVERTER to "case-converter",
				Tool.LINE_TOOLS to "line-tools",
				Tool.TEXT_STATISTICS to "text-statistics",
				Tool.DATA_FORMAT to "data-format-converter",
				Tool.XML to "xml-formatter",
				Tool.SQL to "sql-formatter",
				Tool.MARKDOWN to "markdown-to-html",
				Tool.JSONPATH to "jsonpath-tester",
				Tool.CRON to "cron-explainer",
				Tool.TIME_ZONE to "time-zone-converter",
				Tool.QR to "qr-code-generator",
				Tool.BARCODE to "barcode-generator",
				Tool.PASSWORD to "password-generator",
				Tool.LOREM to "lorem-ipsum-generator",
				Tool.RANDOM_NUMBER to "random-number-generator",
				Tool.RANDOM_PORT to "random-port-generator",
				Tool.PASSWORD_HASHING to "password-hashing",
				Tool.KEY_PAIR to "key-pair-generator",
				Tool.CERTIFICATE to "certificate-decoder",
				Tool.BASIC_AUTH to "basic-auth",
				Tool.SUBNET to "subnet-calculator",
				Tool.URL_PARSER to "url-parser",
				Tool.NUMBER_BASE to "number-base-converter",
				Tool.DATA_SIZE to "data-size-converter",
				Tool.CHMOD to "chmod-calculator",
				Tool.COLOR to "color-converter",
			),
			Tool.entries.associateWith { it.id },
		)
	}

	@Test
	fun `the README tool table is the registry, by category and id`() {
		val listed = File("README.md").readLines()
			.map { line -> line.trim('|').split('|').map(String::trim) }
			.filter { it.size == 5 && it[2].startsWith("`") }
			.map { it[0] to it[2].trim('`') }
		assertEquals(Tool.entries.map { it.category.title to it.id }.toSet(), listed.toSet())
		assertEquals(listed.size, listed.toSet().size)
	}

	@Test
	fun `the tool count is stated the same in the description, the README and the image label`() {
		val count = "${Tool.entries.size} tools"
		assertTrue(count in Project.DESCRIPTION)
		assertTrue(count in File("README.md").readText())
		val meta = YAMLMapper().readTree(File(".github/workflows/release.yml").readText())
			.path("jobs").path("docker").path("steps").toList().single {
				it.path("id").asString() == "meta"
			}
		assertTrue(
			"org.opencontainers.image.description=${Project.DESCRIPTION}" in meta.path("with")
				.path("labels").asString().lines()
		)
	}

	@Test
	fun `every tool view is a UI-scoped Spring component routed under MainLayout from a tools package`() {
		Tool.entries.forEach { tool ->
			val view = tool.view
			assertTrue(ToolView::class.java.isAssignableFrom(view), view.name)
			assertTrue(view.packageName.startsWith("eu.zeletrik.devcore.tools."), view.name)
			assertNotNull(view.getAnnotation(SpringComponent::class.java), view.name)
			assertNotNull(view.getAnnotation(UIScope::class.java), view.name)
			assertEquals(MainLayout::class, view.getAnnotation(Route::class.java).layout, view.name)
			assertTrue(Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(tool.id), tool.id)
		}
		assertEquals(Tool.entries.size, Tool.entries.map { it.id }.toSet().size)
		listOf(HomeView::class.java, AboutView::class.java).forEach {
			assertEquals(MainLayout::class, it.getAnnotation(Route::class.java).layout, it.name)
		}
		assertEquals(MainLayout::class, NotFoundView::class.java.getAnnotation(ParentLayout::class.java).value)
	}

	@Test
	fun `every tool has its own icon`() {
		assertEquals(Tool.entries.size, Tool.entries.map { it.icon }.toSet().size)
	}

	private val verbs = setOf(
		"Build",
		"Calculate",
		"Compare",
		"Compute",
		"Convert",
		"Count",
		"Describe",
		"Draw",
		"Encode",
		"Escape",
		"Format",
		"Generate",
		"Hash",
		"Inspect",
		"Lay",
		"Percent-encode",
		"Pick",
		"Pretty-print",
		"Query",
		"Read",
		"Show",
		"Sort",
		"Spell",
		"Split",
		"Translate",
		"Try",
		"Write",
	)

	@Test
	fun `descriptions start with a verb and have no final period`() {
		Tool.entries.forEach {
			assertTrue(it.description.substringBefore(' ').trimEnd(',') in verbs, it.description)
			assertTrue(!it.description.endsWith("."), it.description)
		}
	}

	@Test
	fun `tool logic is plain Kotlin without Vaadin`() {
		val offenders = sources
			.filter { it.path.contains("/tools/") && !it.name.endsWith("View.kt") }
			.filter { file -> file.readLines().any { it.startsWith("import com.vaadin") } }
			.map { it.name }
		assertEquals(emptyList(), offenders)
	}

	@Test
	fun `production code logs in two places only and never prints, resolves hosts or writes temp files`() {
		val forbidden = Regex(
			"""println\(|System\.(out|err)|printStackTrace|createTemp|InetAddress\.get(All)?ByName|""" +
					"""openConnection\(|openStream\(|java\.net\.http""",
		)
		assertEquals(emptyList(), sources.filter { forbidden.containsMatchIn(it.readText()) }.map { it.name })
		assertEquals(
			setOf("ToolView.kt", "ToolCatalog.kt"),
			sources.filter { "LoggerFactory.getLogger" in it.readText() }.map { it.name }.toSet(),
		)
	}

	@Test
	fun `every enum with a label has distinct, non-blank labels`() {
		val enums = sources.flatMap { file ->
			val pkg = file.readLines().first { it.startsWith("package ") }.removePrefix("package ")
			Regex("""enum class (\w+)\([^)]*val label: String""").findAll(file.readText())
				.map { "$pkg.${it.groupValues[1]}" }
		}
		assertTrue(enums.size >= 20, enums.toString())
		enums.forEach { name ->
			val labels =
				Class.forName(name).enumConstants.map { it.javaClass.getMethod("getLabel").invoke(it) as String }
			assertEquals(labels.size, labels.toSet().size, name)
			assertTrue(labels.all { it.isNotBlank() }, name)
		}
	}

	@Test
	fun `no British spelling in the sources`() {
		val british =
			Regex("""\b(colour|recognised|flavour|honour|behaviour|centre|licence)\b""", RegexOption.IGNORE_CASE)
		val offenders = File("src/main").walkTopDown()
			.filter { it.isFile && (it.extension == "kt" || it.extension == "yaml") }
			.flatMap { file ->
				file.readLines()
					.filter { line -> british.containsMatchIn(line.replace("\"colour\"", "")) }
					.map { "${file.name}: $it" }
			}
			.toList()
		assertEquals(emptyList(), offenders)
	}
}
