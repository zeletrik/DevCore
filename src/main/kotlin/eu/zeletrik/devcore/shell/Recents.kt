package eu.zeletrik.devcore.shell

import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope

/** The last tools opened in this browser, most recent first, kept in a cookie like favorites. */
@SpringComponent
@UIScope
class Recents(catalog: ToolCatalog) {
	private val cookie = ToolCookie(COOKIE)
	private val tools = cookie.read().orEmpty().filter(catalog::isVisible).take(LIMIT).toMutableList()

	init {
		cookie.write(tools) // refreshes Max-Age on every visit and drops hidden ids
	}

	val list: List<Tool> get() = tools

	fun record(tool: Tool) {
		tools.remove(tool)
		tools.add(0, tool)
		if (tools.size > LIMIT) tools.removeLast()
		cookie.write(tools)
	}

	companion object {
		const val COOKIE = "devcore-recent"
		const val LIMIT = 5
	}
}
