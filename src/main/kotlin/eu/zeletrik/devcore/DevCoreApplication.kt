package eu.zeletrik.devcore

import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.page.AppShellConfigurator
import com.vaadin.flow.component.page.ColorScheme
import com.vaadin.flow.theme.aura.Aura
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
@StyleSheet(Aura.STYLESHEET)
@ColorScheme(ColorScheme.Value.LIGHT_DARK)
class DevCoreApplication : AppShellConfigurator

fun main(args: Array<String>) {
	runApplication<DevCoreApplication>(*args)
}
