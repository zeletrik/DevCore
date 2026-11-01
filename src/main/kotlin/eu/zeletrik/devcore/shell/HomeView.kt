package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.card.Card
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.router.RouterLink

@Route("", layout = MainLayout::class)
@PageTitle("DevCore")
class HomeView(favorites: Favorites, recents: Recents, catalog: ToolCatalog) : VerticalLayout() {
	init {
		add(Paragraph(Project.INTRO).apply { style.set("margin", "0").set("color", "var(--vaadin-text-color-secondary)") })
		val tools = catalog.visible
		val starred = tools.filter { it in favorites }
		if (starred.isNotEmpty()) add(H2("Favorites"), cards(starred))
		if (recents.list.isNotEmpty()) add(H2("Recent"), cards(recents.list))
		val byCategory = tools.groupBy { it.category }
		Category.sorted().forEach { category ->
			byCategory[category]?.let { add(H2(category.title), cards(it)) }
		}
	}

	private fun cards(tools: List<Tool>) = Div().apply {
		setWidthFull()
		style.set("display", "grid")
			.set("grid-template-columns", "repeat(auto-fill, minmax(16rem, 1fr))")
			.set("gap", "1rem")
		tools.forEach { tool ->
			val card = Card().apply {
				setHeaderPrefix(tool.icon.create())
				setTitle(title(tool.title))
				setSubtitle(subtitle(tool.description))
				setWidthFull()
			}
			add(RouterLink(tool.view).apply {
				add(card)
				element.setAttribute("title", "${tool.title}: ${tool.description}")
				style.set("text-decoration", "none").set("color", "inherit")
			})
		}
	}

	// Every card is one title line plus three description lines tall, whatever the text, so all cards match.
	private fun title(text: String) = Div(text).apply {
		element.setAttribute("role", "heading")
		element.setAttribute("aria-level", "3")
		style.set("white-space", "nowrap").set("overflow", "hidden").set("text-overflow", "ellipsis")
	}

	private fun subtitle(text: String) = Span(text).apply {
		style.set("display", "-webkit-box").set("-webkit-box-orient", "vertical").set("-webkit-line-clamp", "3")
			.set("overflow", "hidden").set("height", "3lh")
	}
}
