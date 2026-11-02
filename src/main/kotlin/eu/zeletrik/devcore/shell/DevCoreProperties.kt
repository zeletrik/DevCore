package eu.zeletrik.devcore.shell

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Operator settings: `DEVCORE_TOOLS_HIDDEN` and `DEVCORE_FAVORITES_DEFAULT`, comma-separated tool ids, and
 * `DEVCORE_REPOSITORY_URL`, which application.yaml sets to DevCore's own repository.
 */
@ConfigurationProperties("devcore")
data class DevCoreProperties(
	val tools: Tools = Tools(),
	val favorites: FavoriteDefaults = FavoriteDefaults(),
	val repository: Repository,
) {
	data class Tools(val hidden: List<String> = emptyList())

	data class FavoriteDefaults(val default: List<String> = emptyList())

	data class Repository(val url: String)
}
