package eu.zeletrik.devcore.shell

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * The browser may load from and connect to DevCore's own origin only. Vaadin Flow bootstraps with inline script and
 * runs server-sent JavaScript through `new Function`, hence the inline/eval allowances. Framing stays open so
 * same-site dashboards can embed tools.
 */
@Component
class ContentSecurityPolicyFilter : OncePerRequestFilter() {
	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
		response.setHeader("Content-Security-Policy", POLICY)
		chain.doFilter(request, response)
	}

	companion object {
		const val POLICY = "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
			"style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; " +
			"object-src 'none'; base-uri 'self'; form-action 'self'"
	}
}
