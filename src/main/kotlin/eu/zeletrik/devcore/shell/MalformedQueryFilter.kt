package eu.zeletrik.devcore.shell

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Tomcat finds an undecodable query parameter (a stray `%`, `%zz`, bytes that aren't UTF-8) only when something first
 * reads the parameters, then throws, and the servlet container logs the exception, raw parameter value included, at
 * ERROR. Reading them here first answers such a link with a plain 400 and keeps the value, which may be a token or a
 * password pasted into a deep link, out of the log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class MalformedQueryFilter : OncePerRequestFilter() {
	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
		try {
			request.parameterMap
		} catch (_: IllegalStateException) {
			response.status = HttpServletResponse.SC_BAD_REQUEST
			response.contentType = "text/plain;charset=UTF-8"
			response.writer.write(MESSAGE)
			return
		}
		chain.doFilter(request, response)
	}

	companion object {
		const val MESSAGE = "Malformed link: its query must be percent-encoded UTF-8 (write % itself as %25)"
	}
}
