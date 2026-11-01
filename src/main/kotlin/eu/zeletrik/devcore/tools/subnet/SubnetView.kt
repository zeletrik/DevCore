package eu.zeletrik.devcore.tools.subnet

import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textLine

/** IPv4 and IPv6 network details from an address, CIDR or netmask. */
@Route("subnet-calculator", layout = MainLayout::class)
@SpringComponent
@UIScope
class SubnetView : TextToolView<TextField>(Tool.SUBNET, textLine("Address, CIDR or address and netmask")) {
	internal val output = OutputField("Details")

	init {
		add(input, output)
	}

	override fun render(text: String) {
		output.value = Subnets.describe(text)
	}

	override fun clearOutputs() = output.clear()
}
