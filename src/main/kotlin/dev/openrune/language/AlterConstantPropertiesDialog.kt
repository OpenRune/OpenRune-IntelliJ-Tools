package dev.openrune.language

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Dialog that displays all properties from Alter constant provider for a given prefix.
 * 
 * @author Auto-generated
 */
class AlterConstantPropertiesDialog(
    private val prefix: String,
    private val properties: Map<String, String>
) : DialogWrapper(true) {
    
    private var table: JBTable? = null
    
    init {
        title = "Alter Constant Provider: $prefix"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)
        
        // Create table model
        val columnNames = arrayOf("Key", "Value")
        val data = properties.entries.map { arrayOf(it.key, it.value) }.toTypedArray()
        val model = object : DefaultTableModel(data, columnNames) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        
        // Create table
        table = JBTable(model)
        table!!.setShowGrid(true)
        table!!.setIntercellSpacing(Dimension(1, 1))
        table!!.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS)
        table!!.tableHeader.reorderingAllowed = false
        
        // Set column widths
        table!!.columnModel.getColumn(0).preferredWidth = 300
        table!!.columnModel.getColumn(1).preferredWidth = 200
        
        // Add scroll pane
        val scrollPane = JBScrollPane(table)
        scrollPane.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Add info label
        val infoLabel = JLabel("Properties from Alter Constant Provider (${properties.size} entries)")
        infoLabel.border = BorderFactory.createEmptyBorder(10, 10, 5, 10)
        panel.add(infoLabel, BorderLayout.NORTH)
        
        return panel
    }
    
    override fun doOKAction() {
        close(OK_EXIT_CODE)
    }
}

