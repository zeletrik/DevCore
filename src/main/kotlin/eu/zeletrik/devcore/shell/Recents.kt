package eu.zeletrik.devcore.shell

import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope

/** The last tools opened in this browser, most recent first, kept in a cookie like favorites. */
@SpringComponent
@UIScope
class Recents(private val catalog: ToolCatalog) {
	private val cookie = ToolCookie(COOKIE)
	// final: kotlin-spring makes component members open, and an explicit backing field needs a final property.
	final val list: List<Tool>
		field = cookie.read().orEmpty().filter(catalog::isVisible).take(LIMIT).toMutableList()

	init {
		cookie.write(list) // refreshes Max-Age on every visit and drops hidden ids
	}

	fun record(tool: Tool) {
		list.remove(tool)
		list.add(0, tool)
		if (list.size > LIMIT) list.removeLast()
		cookie.write(list)
	}

	companion object {
		const val COOKIE = "devcore-recent"
		const val LIMIT = 5
	}
}
