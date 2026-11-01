package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Html
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.ListItem
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.html.Pre
import com.vaadin.flow.component.html.UnorderedList
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import eu.zeletrik.devcore.tools.markdown.MarkdownToHtml
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import tools.jackson.databind.json.JsonMapper

private const val BUNDLED = "META-INF/devcore/"

/** Version, release notes and licenses. Not a tool: it isn't in the registry, search, Home, favorites or recents. */
@Route("about", layout = MainLayout::class)
@PageTitle("About — DevCore")
class AboutView(buildProperties: ObjectProvider<BuildProperties>) : VerticalLayout() {
	private val build = buildProperties.ifAvailable
	internal val version = Paragraph("Version ${build?.version ?: "development"}")
	internal val built = Paragraph("Built ${build?.time ?: "locally"}")
	internal val whatsNew = releaseNotes(bundled("CHANGELOG.md"))
	internal val dependencies = UnorderedList().apply {
		libraries().forEach { add(ListItem(it)) }
	}

	init {
		add(
			H2("About DevCore"),
			Paragraph(Project.DESCRIPTION),
			version,
			built,
			HorizontalLayout(
				link(Project.REPOSITORY_URL, "GitHub repository"),
				link("${Project.REPOSITORY_URL}/releases", "Releases"),
				link("${Project.REPOSITORY_URL}/security/policy", "Security policy"),
			),
			H3("What's new"),
			whatsNew,
			H3("Licenses"),
			Paragraph("DevCore is released under the MIT License:"),
			Pre(bundled("LICENSE").orEmpty()),
			Paragraph(
				"Bundled data: the EFF Large Wordlist (CC BY 3.0 US) for passphrases, and color-name (MIT) for the CSS color names."
			),
			Paragraph("Third-party libraries, each under its own license:"),
			dependencies,
		)
	}

	private fun link(href: String, text: String) = Anchor(href, text).openInNewTab()

	private fun bundled(name: String) = javaClass.classLoader.getResource(BUNDLED + name)?.readText()

	/** One line per library the jar ships: name, version and licenses, from the build's license report. */
	private fun libraries(): List<String> {
		val report = bundled("dependencies.json") ?: return emptyList()
		// Jackson's JsonNode has a map() of its own that shadows Kotlin's, hence the lists.
		return JsonMapper().readTree(report).path("dependencies").toList().map { dependency ->
			val licenses = dependency.path("moduleLicenses").toList()
				.map { it.path("moduleLicense").asString() }
				.filter { it.isNotEmpty() }
				.joinToString(" / ")
				.ifEmpty { "not declared" }
			"${dependency.path("moduleName").asString()} ${dependency.path("moduleVersion").asString()} — $licenses"
		}
	}

	companion object {
		/**
		 * The changelog as HTML: it is DevCore's own release notes, bundled at build time, and is still cleaned with jsoup
		 * so only formatting and links survive. Without a changelog (a local build), a short note instead.
		 */
		internal fun releaseNotes(markdown: String?): Component {
			if (markdown.isNullOrBlank()) return Paragraph("No release notes in this build")
			val safelist = Safelist.relaxed().addEnforcedAttribute("a", "rel", "noopener noreferrer")
			return Html("<div>" + Jsoup.clean(MarkdownToHtml.convert(markdown), safelist) + "</div>")
		}
	}
}
