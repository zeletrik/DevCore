package eu.zeletrik.devcore

import eu.zeletrik.devcore.shell.Project
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The files that build, ship and describe DevCore, read as text from the checkout. */
class RepositoryFilesTest {
	private fun read(path: String) = File(path).readText()

	private fun yaml(path: String) = YAMLMapper().readTree(read(path))

	/** The text under a README `## ` heading, up to the next one. */
	private fun section(heading: String) = read("README.md").substringAfter("\n## $heading\n").substringBefore("\n## ")

	private val workflows = File(".github/workflows").listFiles { file -> file.extension == "yml" }
		.orEmpty()
		.map { it.path }

	@Test
	fun `the image is pinned, layered, sized to its container and health-checked without extra packages`() {
		val lines = read("Dockerfile").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
		val froms = lines.filter { it.startsWith("FROM ") }
		val base = Regex("FROM (--platform=\\\$BUILDPLATFORM )?(eclipse-temurin:25-jre@sha256:[0-9a-f]{64})( AS layers)?")
		assertEquals(2, froms.size, froms.toString())
		assertEquals(1, froms.map { base.matchEntire(it)?.groupValues?.get(2) }.distinct().size, "both stages share one digest")
		assertTrue(froms.first().startsWith("FROM --platform=\$BUILDPLATFORM "), "layers are split on the build platform")
		val runtime = lines.drop(lines.lastIndexOf(froms.last()))
		assertTrue(runtime.none { it.startsWith("RUN") }, "the runtime stage needs no emulation")
		assertTrue("ENV JAVA_TOOL_OPTIONS=\"-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError\"" in runtime)
		assertTrue("USER 1000:1000" in runtime)
		val healthcheck = runtime.dropWhile { !it.startsWith("HEALTHCHECK") }.take(2).joinToString(" ")
		assertTrue("--interval=30s --timeout=5s --start-period=30s --retries=3" in healthcheck, healthcheck)
		assertTrue("/dev/tcp/127.0.0.1/\${SERVER_PORT:-8080}" in healthcheck, healthcheck)
		assertTrue("\$SERVER_SERVLET_CONTEXT_PATH" in healthcheck && "/actuator/health" in healthcheck, healthcheck)
		assertEquals("ENTRYPOINT [\"java\", \"-jar\", \"devcore.jar\"]", lines.last())
	}

	@Test
	fun `every action is pinned to a commit with its version alongside`() {
		assertEquals(setOf(".github/workflows/release.yml", ".github/workflows/test-verify.yml"), workflows.toSet())
		val uses = workflows.flatMap { read(it).lines() }
			.map { it.trim() }
			.filter { it.startsWith("uses:") || it.startsWith("- uses:") }
		assertTrue(uses.size >= 10, uses.toString())
		uses.forEach { assertTrue(Regex("""(- )?uses: [\w.-]+/[\w./-]+@[0-9a-f]{40} # v\d+\.\d+\.\d+""").matches(it), it) }
	}

	@Test
	fun `dependabot checks actions, gradle and docker weekly`() {
		val config = yaml(".github/dependabot.yml")
		assertEquals(2, config.path("version").asInt())
		val updates = config.path("updates").toList()
		assertEquals(
			setOf("github-actions", "gradle", "docker"),
			updates.map { it.path("package-ecosystem").asString() }.toSet(),
		)
		updates.forEach {
			assertEquals("/", it.path("directory").asString())
			assertEquals("weekly", it.path("schedule").path("interval").asString())
		}
	}

	@Test
	fun `a release commits the changelog with the version`() {
		val plugins = JsonMapper().readTree(read(".releaserc.json")).path("plugins").toList()
		val names = plugins.map { if (it.isArray) it[0].asString() else it.asString() }
		assertTrue(
			names.indexOf("@semantic-release/changelog") in 0..<names.indexOf("@semantic-release/git"),
			names.toString(),
		)
		assertEquals(
			"# Changelog",
			plugins[names.indexOf("@semantic-release/changelog")][1].path("changelogTitle").asString(),
		)
		val assets = plugins[names.indexOf("@semantic-release/git")][1].path("assets").toList().map { it.asString() }
		assertEquals(listOf("gradle.properties", "CHANGELOG.md"), assets)
		val release = yaml(".github/workflows/release.yml").path("jobs").path("release").path("steps").toList()
			.single { it.path("id").asString() == "release" }
		assertTrue("@semantic-release/changelog@7" in release.path("with").path("extra_plugins").asString().lines())
	}

	@Test
	fun `the image is labelled with DevCore's description`() {
		val meta = yaml(".github/workflows/release.yml").path("jobs").path("docker").path("steps").toList()
			.single { it.path("id").asString() == "meta" }
		assertTrue(
			"org.opencontainers.image.description=${Project.DESCRIPTION}" in meta.path("with").path("labels").asString().lines()
		)
	}

	@Test
	fun `the readme warns that a token in a link is a credential`() {
		listOf(section("Deep links"), section("Privacy and trust model")).forEach {
			assertTrue("A token in a link is a credential" in it && "browser history" in it && "proxy access log" in it, it)
		}
	}

	@Test
	fun `the readme covers the proxy headers, the secure cookie and the loopback port`() {
		val proxy = section("Behind a reverse proxy")
		assertTrue("must overwrite the `X-Forwarded-*` headers, not append to them" in proxy, proxy)
		val row = section("Configuration").lines().single { it.startsWith("| `SERVER_SERVLET_SESSION_COOKIE_SECURE` |") }
		assertTrue("`true`" in row && "HTTPS" in row, row)
		assertTrue(read("compose.yaml").lines().any { it.trim() == "# - \"127.0.0.1:8080:8080\"" })
	}

	@Test
	fun `the readme shows the home screenshot and the release badge`() {
		val readme = read("README.md")
		assertTrue(Regex("""!\[[^]]+]\(docs/images/home\.png\)""").containsMatchIn(readme))
		val screenshot = ImageIO.createImageInputStream(File("docs/images/home.png")).use { stream ->
			ImageIO.getImageReaders(stream).next().run { input = stream; getWidth(0) to getHeight(0) }
		}
		assertEquals(1280 to 900, screenshot)
		assertTrue("https://github.com/zeletrik/devcore/actions/workflows/release.yml/badge.svg" in readme)
	}

	@Test
	fun `the readme credits third parties, explains terminal reload and fixes the traefik example`() {
		val thirdParty = section("Third-party")
		assertTrue("NOTICE" in thirdParty && "EFF large word list" in thirdParty && "color-name" in thirdParty, thirdParty)
		assertTrue("`./gradlew -t classes`" in section("Run it"))
		val traefik = section("Behind a reverse proxy").substringAfter("```yaml").substringBefore("```")
		assertTrue("- traefik.enable=true" in traefik, traefik)
		assertTrue(
			"redirectregex.regex=^(https?://[^/]+/devcore)$$" in traefik && "redirectregex.replacement=\$\${1}/" in traefik,
			traefik,
		)
		assertTrue("middlewares=devcore-slash,devcore-strip" in traefik, traefik)
	}

	@Test
	fun `the security policy and contributing guide say what they must`() {
		val security = read("SECURITY.md")
		assertTrue(
			"private vulnerability reporting" in security && "Only the latest release receives security fixes" in security
		)
		val contributing = read("CONTRIBUTING.md")
		listOf("JDK 25", "`JAVA_HOME`", "`./gradlew test detekt`", "Conventional Commits", "## Adding a tool",
			"Never log, store or persist user input", "No outbound calls", "No network lookups").forEach {
			assertTrue(it in contributing, it)
		}
	}

	@Test
	fun `the sponsor button points to Buy Me a Coffee`() {
		assertEquals("zeletrik", yaml(".github/FUNDING.yml").path("buy_me_a_coffee").asString())
	}

	@Test
	fun `issue forms, the pull request template and the wrapper checksum are in place`() {
		listOf("bug_report", "tool_request").forEach {
			val form = yaml(".github/ISSUE_TEMPLATE/$it.yml")
			assertTrue(form.path("name").asString().isNotEmpty() && form.path("description").asString().isNotEmpty(), it)
			assertTrue(form.path("body").toList().any { field -> field.path("validations").path("required").asBoolean() }, it)
		}
		assertEquals(false, yaml(".github/ISSUE_TEMPLATE/config.yml").path("blank_issues_enabled").asBoolean(true))
		assertTrue(read(".github/pull_request_template.md").contains("`./gradlew test detekt`"))
		val wrapper = Properties().apply { File("gradle/wrapper/gradle-wrapper.properties").reader().use(::load) }
		assertTrue(wrapper.getProperty("distributionUrl").endsWith("/gradle-9.8.2-bin.zip"))
		assertEquals(
			"21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607",
			wrapper.getProperty("distributionSha256Sum"),
		)
	}
}
