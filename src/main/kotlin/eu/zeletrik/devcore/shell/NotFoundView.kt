package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.BeforeEnterEvent
import com.vaadin.flow.router.ErrorParameter
import com.vaadin.flow.router.HasErrorParameter
import com.vaadin.flow.router.NotFoundException
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.ParentLayout
import com.vaadin.flow.router.RouterLink
import com.vaadin.flow.server.HttpStatusCode

@ParentLayout(MainLayout::class)
@PageTitle("Tool not found — DevCore")
class NotFoundView : VerticalLayout(), HasErrorParameter<NotFoundException> {
	init {
		add(H2("Tool not found"), RouterLink("Go to Home", HomeView::class.java))
	}

	override fun setErrorParameter(event: BeforeEnterEvent, parameter: ErrorParameter<NotFoundException>) =
		HttpStatusCode.NOT_FOUND.code
}
