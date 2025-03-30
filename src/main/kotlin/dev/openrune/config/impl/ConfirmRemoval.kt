package dev.openrune.config.impl

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.*

class ConfirmRemoval : DialogWrapper(true) {

    private val messageLabel = JBLabel("Warning: Proceed with caution!", UIManager.getIcon("OptionPane.warningIcon"), JLabel.LEFT)
    init {
        title = "Warning Removal of Config"
        init()
        isOKActionEnabled = false
        Timer(1000) { isOKActionEnabled = true }.start()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(messageLabel, BorderLayout.CENTER)
        return panel
    }
}