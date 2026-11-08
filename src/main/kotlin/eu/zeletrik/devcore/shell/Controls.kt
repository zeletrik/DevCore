package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.select.Select

/** A dropdown over an enum's entries, for five or more choices; [text] gives each entry's visible label. */
fun <E : Enum<E>> enumSelect(label: String, entries: List<E>, initial: E, text: (E) -> String, onChange: () -> Unit) =
	Select<E>().apply {
		this.label = label
		setItems(entries)
		setItemLabelGenerator { text(it) }
		value = initial
		addValueChangeListener { onChange() }
	}

/** Radio buttons over an enum's entries, for two to four choices; [text] gives each entry's visible label. */
fun <E : Enum<E>> choice(label: String, entries: List<E>, initial: E, text: (E) -> String, onChange: () -> Unit) =
	RadioButtonGroup(label, entries).apply {
		setItemLabelGenerator { text(it) }
		value = initial
		addValueChangeListener { onChange() }
	}

/** A wrapping row of options whose labels and inputs share one baseline. */
fun optionsRow(vararg components: Component) = HorizontalLayout(*components).apply {
	defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
	isWrap = true
}
