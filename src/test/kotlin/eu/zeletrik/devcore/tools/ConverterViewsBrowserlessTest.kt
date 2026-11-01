package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.data.value.ValueChangeMode
import eu.zeletrik.devcore.tools.chmod.ChmodView
import eu.zeletrik.devcore.tools.color.ColorView
import eu.zeletrik.devcore.tools.datasize.DataUnit
import eu.zeletrik.devcore.tools.datasize.DataSizeView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class ConverterViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `chmod - checkboxes follow the input and the input follows the checkboxes`() {
		val view = navigate("chmod-calculator?input=640", ChmodView::class.java)
		assertEquals("chmod 640 <file>", view.command.value)
		val ticked = view.boxes.filterValues { it.value }.values.map { it.label }
		assertEquals(listOf("Owner Read", "Owner Write", "Group Read"), ticked)
		test(view.input).setValue("rw-r--r--")
		test(view.boxes.getValue(1 shl 1)).click()
		assertEquals("646", view.input.value)
		assertEquals("rw-r--rw-", view.symbolic.value)
	}

	@Test
	fun `data size defaults to MB`() {
		val view = navigate("data-size-converter?input=1", DataSizeView::class.java)
		assertEquals(DataUnit.MB, view.unit.value)
		assertEquals("8000000 bit", view.output.value.lines().first())
	}

	@Test
	fun `color - swatch from the parsed color, picker writes the input`() {
		val view = navigate("color-converter?input=Red", ColorView::class.java)
		assertTrue(view.swatch.isVisible)
		assertEquals("#ff0000", view.swatch.style.get("background-color"))
		assertEquals("#ff0000", view.picker.value)
		view.pick("#00ff00")
		assertEquals("#00ff00", view.input.value)
		assertEquals("lime", view.name.value)
	}

	@Test
	fun `color - the picker applies while it is still open`() {
		val view = navigate(ColorView::class.java)
		assertEquals(ValueChangeMode.TIMEOUT, view.picker.valueChangeMode)
		assertTrue(view.picker.valueChangeTimeout in 1..250)
	}
}
