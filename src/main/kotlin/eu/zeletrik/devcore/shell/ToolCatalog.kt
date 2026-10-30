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

	fun byId(id: String) = Tool.byId(id)?.takeIf(::isVisible)

	private fun resolve(ids: List<String>, variable: String): Set<Tool> =
		ids.map(String::trim).filter(String::isNotEmpty).distinct().mapNotNullTo(mutableSetOf()) { id ->
			Tool.byId(id) ?: null.also { log.warn("Ignoring unknown tool id '{}' in {}", id, variable) }
		}

	private companion object {
		val log = LoggerFactory.getLogger(ToolCatalog::class.java)
	}
}
