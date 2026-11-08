package eu.zeletrik.devcore.tools.color

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Input
import com.vaadin.flow.component.html.NativeLabel
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textLine

private const val PICK_INTERVAL_MS = 150

/** Any CSS color notation in, every notation out, with a native picker that writes back into the input. */
@Route("color-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class ColorView : TextToolView<TextField>(Tool.COLOR, textLine("Color")) {
	/**
	 * The browser's native color picker; whatever it picks goes into the input as #rrggbb. The page can't close the
	 * browser's picker, so each pick applies while it is still open, and closing it is never a separate step.
	 */
	internal val picker = Input().apply {
		type = "color"
		setId("devcore-color-picker")
		valueChangeMode = ValueChangeMode.TIMEOUT
		valueChangeTimeout = PICK_INTERVAL_MS
		// Only a user's pick: the server also updates the picker after each parse, and that must not overwrite the input.
		addValueChangeListener { if (it.isFromClient) pick(it.value) }
	}
	internal val swatch = Div().apply {
		width = "3rem"
		height = "3rem"
		style.set("border-radius", "0.5rem").set("border", "1px solid var(--vaadin-border-color, #ccc)")
		isVisible = false
	}
	internal val hex = OutputField("Hex")
	internal val rgb = OutputField("RGB")
	internal val hsl = OutputField("HSL")
	internal val hsv = OutputField("HSV")
	internal val cmyk = OutputField("CMYK")
	internal val name = OutputField("Name")

	init {
		val hint = Span("Applies as you pick").apply { style.set("color", "var(--vaadin-text-color-secondary)") }
		val pick = HorizontalLayout(NativeLabel("Pick a color").apply { setFor(picker) }, picker, hint)
			.apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.CENTER }
		add(
			HorizontalLayout(input, swatch).apply {
				defaultVerticalComponentAlignment = FlexComponent.Alignment.END
				setWidthFull()
			},
			pick, hex, rgb, hsl, hsv, cmyk, name,
		)
	}

	internal fun pick(color: String) {
		input.value = color
	}

	override fun render(text: String) {
		val color = Colors.parse(text)
		val notations = Colors.notations(color)
		hex.value = notations.hex
		rgb.value = notations.rgb
		hsl.value = notations.hsl
		hsv.value = notations.hsv
		cmyk.value = notations.cmyk
		name.value = notations.name
		val css = Colors.cssHex(color)
		swatch.style.set("background-color", css)
		swatch.isVisible = true
		picker.value = css
	}

	override fun clearOutputs() {
		listOf(hex, rgb, hsl, hsv, cmyk, name).forEach { it.clear() }
		swatch.isVisible = false
	}
}
