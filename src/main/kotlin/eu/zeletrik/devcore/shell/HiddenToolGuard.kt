package eu.zeletrik.devcore.shell

import com.vaadin.flow.router.NotFoundException
import com.vaadin.flow.server.ServiceInitEvent
import com.vaadin.flow.server.VaadinServiceInitListener
import com.vaadin.flow.spring.annotation.SpringComponent

/** Hidden tools answer like unknown paths, whether reached by deep link or in-app navigation. */
@SpringComponent
class HiddenToolGuard(private val catalog: ToolCatalog) : VaadinServiceInitListener {
	override fun serviceInit(event: ServiceInitEvent) {
		event.source.addUIInitListener { init ->
			init.ui.addBeforeEnterListener { navigation ->
				val tool = Tool.of(navigation.navigationTarget)
				if (tool != null && !catalog.isVisible(tool)) navigation.rerouteToError(NotFoundException::class.java)
			}
		}
	}
}
