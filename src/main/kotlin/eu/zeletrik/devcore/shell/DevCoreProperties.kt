package eu.zeletrik.devcore.shell

import org.springframework.boot.context.properties.ConfigurationProperties

/** Operator settings: `DEVCORE_TOOLS_HIDDEN` and `DEVCORE_FAVORITES_DEFAULT`, comma-separated tool ids. */
@ConfigurationProperties("devcore")
data class DevCoreProperties(
	val tools: Tools = Tools(),
	val favorites: FavoriteDefaults = FavoriteDefaults(),
) {
	data class Tools(val hidden: List<String> = emptyList())

	data class FavoriteDefaults(val default: List<String> = emptyList())
}
