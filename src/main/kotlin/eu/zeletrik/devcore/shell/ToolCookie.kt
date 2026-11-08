package eu.zeletrik.devcore.shell

import com.vaadin.flow.server.VaadinRequest
import com.vaadin.flow.server.VaadinService
import jakarta.servlet.http.Cookie

/** A per-browser list of tool ids, kept in a cookie: ids only, never tool input. */
internal class ToolCookie(private val name: String) {
	/** The stored tools in order, or null when the request carries no such cookie (or there is no request). */
	fun read(): List<Tool>? = VaadinRequest.getCurrent()?.cookies?.firstOrNull { it.name == name }?.let { parse(it.value) }

	fun write(tools: Collection<Tool>) {
		val response = VaadinService.getCurrentResponse() ?: return
		val contextPath = VaadinRequest.getCurrent()?.contextPath.orEmpty()
		response.addCookie(Cookie(name, serialize(tools)).apply {
			maxAge = MAX_AGE_SECONDS
			path = contextPath.ifEmpty { "/" }
			isHttpOnly = true
			setAttribute("SameSite", "Lax")
		})
	}

	companion object {
		const val MAX_AGE_SECONDS = 400 * 24 * 60 * 60 // Chrome's cap

		fun parse(value: String): List<Tool> = value.split('.').mapNotNull(Tool::byId).distinct()

		fun serialize(tools: Collection<Tool>) = tools.joinToString(".") { it.id }
	}
}
