package eu.zeletrik.devcore.shell

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** The tools this instance offers: the registry minus hidden ones. Every surface reads tools through here. */
@Component
class ToolCatalog(properties: DevCoreProperties) {
	private val hidden = resolve(properties.tools.hidden, "DEVCORE_TOOLS_HIDDEN")

	val visible: List<Tool> = Tool.sorted().filter { it !in hidden }

	val defaultFavorites: Set<Tool> = resolve(properties.favorites.default, "DEVCORE_FAVORITES_DEFAULT") - hidden

	fun isVisible(tool: Tool) = tool !in hidden

	private fun resolve(ids: List<String>, variable: String): Set<Tool> =
		ids.map(String::trim).filter(String::isNotEmpty).distinct().mapNotNullTo(mutableSetOf()) { id ->
			val tool = Tool.byId(id)
			if (tool == null) log.warn("Ignoring unknown tool id '{}' in {}", id, variable)
			tool
		}

	private companion object {
		val log = LoggerFactory.getLogger(ToolCatalog::class.java)
	}
}
