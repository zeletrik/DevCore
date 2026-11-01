package eu.zeletrik.devcore

import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.page.AppShellConfigurator
import com.vaadin.flow.component.page.ColorScheme
import com.vaadin.flow.server.AppShellSettings
import com.vaadin.flow.server.PWA
import com.vaadin.flow.theme.aura.Aura
import eu.zeletrik.devcore.shell.Project
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import javax.imageio.ImageIO

/**
 * Installable as an app, but never offline: no service worker, so every page load still comes from the server. `@PWA`
 * already emits the theme-color meta tag; the page settings add the SVG favicon and the description.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@StyleSheet(Aura.STYLESHEET)
@ColorScheme(ColorScheme.Value.LIGHT_DARK)
@PWA(
	name = "DevCore",
	shortName = "DevCore",
	description = Project.DESCRIPTION,
	iconPath = "icons/icon.png",
	themeColor = Project.THEME,
	backgroundColor = Project.THEME,
	offline = false,
)
class DevCoreApplication : AppShellConfigurator {
	override fun configurePage(settings: AppShellSettings) {
		settings.addFavIcon("icon", "icons/favicon.svg", "any")
		settings.addMetaTag("description", Project.DESCRIPTION)
	}
}

fun main(args: Array<String>) {
	// PNGs are written in memory only; ImageIO's default would stage them in a temp file.
	ImageIO.setUseCache(false)
	runApplication<DevCoreApplication>(*args)
}
