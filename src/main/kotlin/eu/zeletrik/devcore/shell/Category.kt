package eu.zeletrik.devcore.shell

/** Sidebar groups and Home sections, shown in alphabetical order of their titles. */
enum class Category(val title: String) {
	CONVERTERS("Converters"),
	DATA("Data & formats"),
	DATE_TIME("Date & time"),
	ENCODING("Encoding"),
	GENERATORS("Generators"),
	NETWORK("Network"),
	SECURITY("Security"),
	TEXT("Text"),
	;

	companion object {
		fun sorted() = entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Category::title))
	}
}
