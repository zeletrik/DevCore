package eu.zeletrik.devcore.shell

import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope

/** Per-browser favorites, kept in a cookie so the first render already knows them. */
@SpringComponent
@UIScope
class Favorites(catalog: ToolCatalog) {
	private val cookie = ToolCookie(COOKIE)

	// A stored choice, even an empty one, always beats the operator's defaults.
	private val tools = (cookie.read()?.filter(catalog::isVisible) ?: catalog.defaultFavorites).toMutableSet()

	init {
		cookie.write(tools) // refreshes Max-Age on every visit and drops hidden ids
	}

	operator fun contains(tool: Tool) = tool in tools

	fun toggle(tool: Tool) {
		if (!tools.remove(tool)) tools += tool
		cookie.write(tools)
	}

	companion object {
		const val COOKIE = "devcore-favorites"
	}
}
