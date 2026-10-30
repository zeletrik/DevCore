package eu.zeletrik.devcore.shell

/** The sidebar as labels: top-level entries as is, grouped tools as "Category/Tool". */
internal fun MainLayout.navTree(): List<String> = nav.items.flatMap { item ->
	if (item.items.isEmpty()) listOf(item.label) else item.items.map { "${item.label}/${it.label}" }
}

/** The category groups expected for these tools, in sidebar order. */
internal fun groupsOf(tools: List<Tool>): List<String> =
	Category.sorted().flatMap { category -> tools.filter { it.category == category }.map { "${category.title}/${it.title}" } }

/** Home's category section headings for these tools. */
internal fun categoryHeadings(tools: List<Tool>): List<String> =
	Category.sorted().filter { category -> tools.any { it.category == category } }.map { it.title }
