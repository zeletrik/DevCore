package eu.zeletrik.devcore.tools.sql

import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea

@Route("sql-formatter", layout = MainLayout::class)
@SpringComponent
@UIScope
class SqlFormatterView : TextToolView<TextArea>(Tool.SQL, textArea("SQL")) {
	internal val dialect = Select<SqlDialect>().apply {
		label = "Dialect"
		setItems(*SqlDialect.entries.toTypedArray())
		setItemLabelGenerator { it.label }
		value = SqlDialect.STANDARD
		addValueChangeListener { recompute() }
	}
	internal val indent = RadioButtonGroup("Indent", 2, 4).apply {
		setItemLabelGenerator { "$it spaces" }
		value = 2
		addValueChangeListener { recompute() }
	}
	internal val uppercase = RadioButtonGroup("Keywords", true, false).apply {
		setItemLabelGenerator { if (it) "Uppercase" else "As written" }
		value = true
		addValueChangeListener { recompute() }
	}
	internal val output = OutputField("Formatted")

	init {
		add(
			HorizontalLayout(dialect, indent, uppercase)
				.apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE; isWrap = true },
			input,
			output,
		)
	}

	override fun render(text: String) {
		output.value = SqlFormat.format(text, dialect.value, indent.value, uppercase.value)
	}

	override fun clearOutputs() = output.clear()
}
