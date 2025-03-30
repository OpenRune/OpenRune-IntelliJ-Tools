package dev.openrune.config.impl

import dev.openrune.config.Section
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class DetailsPanel : JBPanel<JBPanel<*>>(GridBagLayout()) {

    private var currentSection: Section? = null
    var selected: String = ""

    init {
        // Initialize components if needed
    }

    fun update(section: Section?) {
        section?.let {
            currentSection = it
            buildPanel()
        }
    }

    private fun buildPanel() {
        this.removeAll()

        val constraints = GridBagConstraints().apply {
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.NONE
        }

        val defaultDef = currentSection!!.codec.createDefinition()

        var gridY = 0

        currentSection!!.def::class.memberProperties.forEach { property ->
            property.isAccessible = true
            val value = property.getter.call(currentSection!!.def)
            val defaultValue = property.getter.call(defaultDef)

            if (value != defaultValue) {
                val label = JBLabel("${property.name}: ")
                val inputComponent = createInputComponent(value)

                constraints.gridx = 0
                constraints.gridy = gridY
                constraints.gridwidth = 1
                this.add(label, constraints)

                constraints.gridx = 1
                constraints.gridy = gridY
                constraints.gridwidth = 1
                this.add(inputComponent, constraints)

                gridY++
                constraints.gridy = gridY
                constraints.gridwidth = 2
                this.add(Box.createVerticalStrut(10), constraints)

                gridY++
            }
        }

        this.revalidate()
        this.repaint()
    }

    private fun createInputComponent(com: Any?): JComponent {
        return when (com) {
            is String -> JTextField(com, 15)
            is List<*> -> ComboBox(com.map { it.toString() }.toTypedArray())
            is Int -> JSpinner(SpinnerNumberModel(com, Int.MIN_VALUE, Int.MAX_VALUE, 1))
            is Boolean -> JCheckBox()
            else -> JBLabel(com?.toString() ?: "N/A")
        }.apply {
            when (this) {
                is JTextField -> text = com as String
                is JComboBox<*> -> selectedItem = com
                is JSpinner -> value = com as Int
                is JCheckBox -> isSelected = com as? Boolean ?: false
            }
        }
    }
}
