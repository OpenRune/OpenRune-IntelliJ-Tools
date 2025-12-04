package io.blurite.rscm.language

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.io.DataInputStream
import javax.swing.*
import javax.swing.event.ListSelectionEvent

/**
 * Custom editor for .dat files with split view:
 * - Left: Table list showing all types (items, objects, etc.)
 * - Right: Code editor showing gamevals for selected type
 */
class DatFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {
    
    companion object {
        // Key to store the table name to select when opening the file
        val SELECTED_TABLE_KEY = Key.create<String>("dat.file.selected.table")
        // Key to store the key name to navigate to when opening the file
        val SELECTED_KEY_KEY = Key.create<String>("dat.file.selected.key")
    }
    
    // Data structure: table name -> list of key-value pairs (immutable for safety)
    private val gamevalsData: Map<String, List<String>>
    
    // Cached sorted type names to avoid recalculation
    private val typeNames: List<String>
    
    // UI components
    private val mainComponent: JComponent
    private val splitPane: Splitter?
    private val typeList: JBList<String>?
    private val editor: EditorEx
    private val notePanel: JPanel
    
    init {
        // Decode the file and store data (make immutable)
        gamevalsData = decodeGameValsFromFile()
        typeNames = gamevalsData.keys.sorted()
        
        val hasMultipleTables = typeNames.size > 1
        
        // Get the table name and key to select (if specified via user data)
        val tableToSelect = file.getUserData(SELECTED_TABLE_KEY)?.also {
            file.putUserData(SELECTED_TABLE_KEY, null) // Clear immediately
        }
        val keyToSelect = file.getUserData(SELECTED_KEY_KEY)?.also {
            file.putUserData(SELECTED_KEY_KEY, null) // Clear immediately
        }
        
        val initialTableIndex = if (tableToSelect != null) {
            typeNames.indexOf(tableToSelect).takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        
        // Create code editor with initial content
        val initialText = if (typeNames.isNotEmpty()) {
            formatGamevalsForType(typeNames[initialTableIndex])
        } else {
            "No data available"
        }
        
        val document = EditorFactory.getInstance().createDocument(initialText)
        editor = EditorFactory.getInstance().createEditor(document, project) as EditorEx
        with(editor.settings) {
            isLineNumbersShown = true
            isFoldingOutlineShown = true
            isRightMarginShown = false
        }
        editor.isViewer = true
        editor.document.setReadOnly(true)
        
        if (hasMultipleTables) {
            // Create left panel with list of types using IntelliJ's JBList
            typeList = JBList<String>(typeNames).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                selectedIndex = initialTableIndex.coerceIn(0, typeNames.size - 1)
                cellRenderer = DefaultListCellRenderer().apply {
                    border = JBUI.Borders.empty(4, 8)
                }
            }
            
            // Create a wrapper panel for the left side with proper styling
            val leftPanel = Wrapper().apply {
                background = UIUtil.getListBackground()
                border = JBUI.Borders.empty()
                preferredSize = java.awt.Dimension(200, 0)
            }
            
            val leftScrollPane = JBScrollPane(typeList).apply {
                border = JBUI.Borders.empty()
                background = UIUtil.getListBackground()
                viewport.background = UIUtil.getListBackground()
            }
            
            leftPanel.setContent(leftScrollPane)
            
            // Handle selection changes
            typeList.addListSelectionListener { e: ListSelectionEvent ->
                if (!e.valueIsAdjusting) {
                    val selectedType = typeList.selectedValue ?: return@addListSelectionListener
                    val text = formatGamevalsForType(selectedType)
                    val shouldNavigateToKey = keyToSelect != null && selectedType == tableToSelect
                    ApplicationManager.getApplication().invokeLater {
                        ApplicationManager.getApplication().runWriteAction {
                            editor.document.setReadOnly(false)
                            editor.document.setText(text)
                            editor.document.setReadOnly(true)
                        }
                        // Navigate to key if this is the initial selection - wait for document update
                        if (shouldNavigateToKey) {
                            ApplicationManager.getApplication().invokeLater {
                                if (editor.document.textLength > 0) {
                                    navigateToKey(keyToSelect!!)
                                }
                            }
                        }
                    }
                }
            }
            
            // Navigate to key on initial load if needed - wait for editor to be fully initialized
            if (keyToSelect != null && tableToSelect != null) {
                ApplicationManager.getApplication().invokeLater {
                    // Wait for component to be shown and document to be ready
                    if (editor.component.isShowing && editor.document.textLength > 0) {
                        navigateToKey(keyToSelect)
                    } else {
                        // If not ready yet, try again after a short delay
                        ApplicationManager.getApplication().invokeLater {
                            if (editor.document.textLength > 0) {
                                navigateToKey(keyToSelect)
                            }
                        }
                    }
                }
            }
            
            // Create split pane with proper IntelliJ styling using JBSplitter (theme-aware)
            splitPane = Splitter(false, 0.2f).apply {
                firstComponent = leftPanel
                secondComponent = editor.component
                dividerWidth = JBUI.scale(5)
            }
            
            notePanel = createNotePanel()
            mainComponent = JPanel(BorderLayout()).apply {
                add(notePanel, BorderLayout.NORTH)
                add(splitPane, BorderLayout.CENTER)
            }
        } else {
            // Only one table - just show the editor with note
            typeList = null
            splitPane = null
            notePanel = createNotePanel()
            mainComponent = JPanel(BorderLayout()).apply {
                add(notePanel, BorderLayout.NORTH)
                add(editor.component, BorderLayout.CENTER)
            }
            
            // Navigate to key if needed - wait for editor to be fully initialized
            if (keyToSelect != null) {
                ApplicationManager.getApplication().invokeLater {
                    // Wait for component to be shown and document to be ready
                    if (editor.component.isShowing && editor.document.textLength > 0) {
                        navigateToKey(keyToSelect)
                    } else {
                        // If not ready yet, try again after a short delay
                        ApplicationManager.getApplication().invokeLater {
                            if (editor.document.textLength > 0) {
                                navigateToKey(keyToSelect)
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Decode the .dat file and return an immutable map of table names to their key-value pairs.
     */
    private fun decodeGameValsFromFile(): Map<String, List<String>> {
        return try {
            val data = mutableMapOf<String, MutableList<String>>()
            DataInputStream(file.inputStream).use { input ->
                val tableCount = input.readInt()
                
                repeat(tableCount) {
                    val nameLength = input.readShort().toInt()
                    val nameBytes = ByteArray(nameLength)
                    input.readFully(nameBytes)
                    val tableName = String(nameBytes, Charsets.UTF_8)
                    
                    val itemCount = input.readInt()
                    val items = ArrayList<String>(itemCount) // Pre-size list
                    repeat(itemCount) {
                        val itemLength = input.readShort().toInt()
                        val itemBytes = ByteArray(itemLength)
                        input.readFully(itemBytes)
                        items.add(String(itemBytes, Charsets.UTF_8))
                    }
                    data[tableName] = items
                }
            }
            data.toMap() // Make immutable
        } catch (e: Exception) {
            mapOf("Error" to listOf("Error decoding .dat file: ${e.message}\n${e.stackTraceToString()}"))
        }
    }
    
    /**
     * Create a note panel explaining that these are internal non-editable gamevals.
     */
    private fun createNotePanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 12, 8, 12)
            background = UIUtil.getPanelBackground()
        }
        
        val noteLabel = JBLabel("<html>These are internal non-editable gamevals.</html>").apply {
            // Use a lighter, more subtle color
            foreground = UIUtil.getLabelDisabledForeground().let { color ->
                // Make it lighter by increasing brightness
                val hsb = java.awt.Color.RGBtoHSB(color.red, color.green, color.blue, null)
                java.awt.Color.getHSBColor(hsb[0], hsb[1] * 0.5f, (hsb[2] + 0.3f).coerceAtMost(1.0f))
            }
        }
        
        panel.add(noteLabel, BorderLayout.WEST)
        return panel
    }
    
    /**
     * Format gamevals for a specific type as text.
     */
    private fun formatGamevalsForType(type: String): String {
        val items = gamevalsData[type] ?: return "Type '$type' not found"
        // Pre-size StringBuilder for better performance
        val estimatedSize = items.sumOf { it.length } + type.length + 20
        return buildString(estimatedSize) {
            append("[gamevals.$type]\n")
            items.forEach { append(it).append('\n') }
        }
    }
    
    override fun getComponent(): JComponent = mainComponent
    
    override fun getPreferredFocusedComponent(): JComponent? = typeList ?: editor.contentComponent
    
    override fun getName(): String = "DAT Viewer"
    
    override fun setState(state: FileEditorState) {
        // No state to set
    }
    
    override fun isModified(): Boolean = false
    
    override fun isValid(): Boolean = true
    
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        // No properties to change
    }
    
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        // No properties to change
    }
    
    override fun getFile(): VirtualFile = file
    
    override fun getCurrentLocation(): FileEditorLocation? = null
    
    /**
     * Select a specific table in the editor (used when file is already open).
     * Optionally navigate to a specific key within that table.
     */
    fun selectTable(tableName: String, keyToFind: String? = null) {
        if (typeList != null && gamevalsData.containsKey(tableName)) {
            // Multiple tables - update selection
            val index = typeNames.indexOf(tableName)
            if (index >= 0) {
                typeList.selectedIndex = index
                // Selection listener will update content, then navigate if needed
                if (keyToFind != null) {
                    ApplicationManager.getApplication().invokeLater {
                        navigateToKey(keyToFind)
                    }
                }
            }
        } else if (keyToFind != null && (gamevalsData.containsKey(tableName) || gamevalsData.size == 1)) {
            // Single table - just navigate to the key
            ApplicationManager.getApplication().invokeLater {
                navigateToKey(keyToFind)
            }
        }
    }
    
    /**
     * Navigate to a specific key in the editor by searching for it.
     */
    private fun navigateToKey(key: String) {
        val document = editor.document
        val text = document.text
        if (text.isEmpty()) return
        
        val keyWithEquals = "$key="
        var offset = 0
        
        // Search line by line for better memory efficiency
        text.lineSequence().forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith(keyWithEquals) || trimmed == key) {
                val keyPosition = line.indexOf(key)
                if (keyPosition >= 0) {
                    val finalOffset = (offset + keyPosition).coerceIn(0, document.textLength)
                    editor.caretModel.moveToOffset(finalOffset)
                    editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                    return
                }
            }
            offset += line.length + 1 // +1 for newline
        }
    }
    
    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}

