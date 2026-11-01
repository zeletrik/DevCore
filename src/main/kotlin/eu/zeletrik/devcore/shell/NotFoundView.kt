package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.Text
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.BeforeEnterEvent
import com.vaadin.flow.router.ErrorParameter
import com.vaadin.flow.router.HasErrorParameter
import com.vaadin.flow.router.NotFoundException
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.ParentLayout
import com.vaadin.flow.router.RouterLink
import com.vaadin.flow.server.HttpStatusCode

/**
 * The 404 page inside the main layout: unknown paths land here, and so do hidden tools (see [HiddenToolGuard]).
 * An error view has no route of its own, hence @ParentLayout rather than @Route.
 */
@ParentLayout(MainLayout::class)
@PageTitle("Tool not found — DevCore")
class NotFoundView : VerticalLayout(), HasErrorParameter<NotFoundException> {
	init {
		val guidance = Paragraph(
			Text("Search with Ctrl+K / ⌘K, or go to "),
			RouterLink("Home", HomeView::class.java),
			Text("."),
		)
		add(H2("Tool not found"), guidance)
	}

	override fun setErrorParameter(event: BeforeEnterEvent, parameter: ErrorParameter<NotFoundException>) =
		HttpStatusCode.NOT_FOUND.code
}
