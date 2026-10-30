package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.KeyModifier
import com.vaadin.flow.component.applayout.AppLayout
import com.vaadin.flow.component.applayout.DrawerToggle
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.sidenav.SideNav
import com.vaadin.flow.component.sidenav.SideNavItem
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import com.vaadin.flow.router.AfterNavigationEvent
import com.vaadin.flow.router.AfterNavigationObserver

class MainLayout(
	private val favorites: Favorites,
	internal val recents: Recents,
	private val catalog: ToolCatalog,
) : AppLayout(), AfterNavigationObserver {
	internal val title = H1().apply {
		style.set("font-size", "1.125rem").set("margin", "0")
	}
	internal val star = Button().apply {
		addThemeVariants(ButtonVariant.TERTIARY)
		addClickListener { currentTool?.let { favorites.toggle(it); renderStar(); renderNav(expandAll = false) } }
	}
	internal val search = TextField("Search tools").apply {
		valueChangeMode = ValueChangeMode.EAGER
		isClearButtonVisible = true
		prefixComponent = VaadinIcon.SEARCH.create()
		setWidthFull()
		addFocusShortcut(Key.KEY_K, KeyModifier.CONTROL)
		addFocusShortcut(Key.KEY_K, KeyModifier.META)
		addValueChangeListener { renderNav() }
	}
	internal val nav = SideNav()
	private val groups = mutableMapOf<Category, SideNavItem>()
	internal val noMatches = Span("No tools found")
	private var currentTool: Tool? = null

	init {
		primarySection = Section.DRAWER
		addToNavbar(DrawerToggle(), title, star)
		addToDrawer(VerticalLayout(search, nav, noMatches).apply { isPadding = true })
		renderNav()
	}

	/** Home, favorites, then one group per category. A search shows every group expanded; a star toggle keeps them as they were. */
	private fun renderNav(expandAll: Boolean = true) {
		val collapsed = if (expandAll) emptySet() else groups.filterValues { !it.isExpanded }.keys
		nav.removeAll()
		groups.clear()
		nav.addItem(SideNavItem("Home", HomeView::class.java, VaadinIcon.HOME.create()))
		val matches = catalog.visible.filter { it.matches(search.value.trim()) }
		matches.filter { it in favorites }.forEach { nav.addItem(item(it).apply { suffixComponent = VaadinIcon.STAR.create() }) }
		val byCategory = matches.groupBy { it.category }
		Category.sorted().forEach { category ->
			val tools = byCategory[category] ?: return@forEach
			val group = SideNavItem(category.title).apply {
				tools.forEach { addItem(item(it)) }
				isExpanded = category !in collapsed
			}
			groups[category] = group
			nav.addItem(group)
		}
		noMatches.isVisible = matches.isEmpty()
	}

	private fun item(tool: Tool) = SideNavItem(tool.title, tool.view, tool.icon.create())

	private fun renderStar() {
		val on = currentTool?.let { it in favorites } == true
		star.icon = (if (on) VaadinIcon.STAR else VaadinIcon.STAR_O).create()
		star.setAriaLabel(if (on) "Remove from favorites" else "Add to favorites")
		star.element.setAttribute("aria-pressed", on.toString())
	}

	override fun afterNavigation(event: AfterNavigationEvent) {
		currentTool = Tool.of(content.javaClass)
		currentTool?.let(recents::record)
		title.text = currentTool?.title ?: if (content is HomeView) "Home" else ""
		star.isVisible = currentTool != null
		renderStar()
	}
}
