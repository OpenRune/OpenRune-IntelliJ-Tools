package dev.openrune.config.impl

import dev.openrune.config.ConfigType
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class CreateConfigDialog(val onConfirmAction: (selectedType: ConfigType, enteredId: String) -> Unit) : DialogWrapper(true) {
    private val comboBox = ComboBox(ConfigType.values().map { it.name.lowercase().replaceFirstChar { it.uppercase() } }.toTypedArray())
    private val intInput = JTextField()

    init {
        title = "Create Config"
        init()
        isOKActionEnabled = false

        intInput.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = validateInput()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = validateInput()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = validateInput()
        })
    }

    private fun validateInput() {
        isOKActionEnabled = intInput.text.isNotBlank()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayout(2, 2, 10, 10))
        panel.add(JBLabel("Config type:"))
        panel.add(comboBox)
        panel.add(JBLabel("ID:"))
        panel.add(intInput)
        return panel
    }

    override fun doOKAction() {
        val selectedOption = comboBox.selectedItem as String
        val number = intInput.text
        onConfirmAction.invoke(ConfigType.valueOf(selectedOption.uppercase()), number)
        super.doOKAction()
    }
}

