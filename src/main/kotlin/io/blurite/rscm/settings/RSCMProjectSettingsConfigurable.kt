package io.blurite.rscm.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class RSCMProjectSettingsConfigurable(
    private val project: Project,
) : Configurable {
    private var settingsFileTextField: TextFieldWithBrowseButton? = null
    private var mappingsPathsList: MutableList<TextFieldWithBrowseButton> = mutableListOf()
    private var mappingsPathsPanel: JPanel? = null
    private var addButton: JButton? = null
    private var pathsGridBag: GridBag? = null
    private var referentialMappingsTextField: TextFieldWithHistory? = null
    private var enableFileProviderCheckBox: JCheckBox? = null
    private var enableAlterConstantProviderCheckBox: JCheckBox? = null
    private var tabbedPane: JTabbedPane? = null
    private var panel: JPanel? = null
    private var mappingsContentPanel: JPanel? = null
    private val settings = RSCMProjectSettings.getInstance(project)

    override fun createComponent(): JComponent? {
        // Create tabbed pane without scroll buttons (always show all tabs)
        tabbedPane = JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT)
        
        // Create mappings panel (directory list)
        val mappingsPanel = createMappingsPanel()
        tabbedPane?.addTab("Mappings", null, mappingsPanel, "Configure mappings directories")
        
        // Create providers panel
        val providersPanel = createProvidersPanel()
        tabbedPane?.addTab("Providers", null, providersPanel, "Enable or disable RSCM providers")
        
        // Note: General tab removed but createGeneralSettingsPanel() method kept for future use
        
        panel = JPanel(BorderLayout())
        panel?.border = JBUI.Borders.empty(10, 15)
        panel?.add(tabbedPane, BorderLayout.CENTER)
        
        // Update UI state based on settings file
        updateUIForSettingsFile()
        
        return panel
    }
    
    /**
     * Create the mappings panel (directory list).
     */
    private fun createMappingsPanel(): JPanel {
        val mappingsPanel = JPanel(BorderLayout())
        mappingsPanel.border = JBUI.Borders.empty(15)

        // Create main content panel with vertical layout
        val contentPanel = JPanel(VerticalLayout(JBUI.scale(12)))
        contentPanel.border = EmptyBorder(0, 0, 0, 0)
        mappingsContentPanel = contentPanel
        
        // Section: Settings File (at the top)
        val settingsFileSection = createSectionPanel(
            "Settings File (Optional)",
            "Use a TOML file to override these settings. Directories in the file are relative to the settings file location."
        )
        
        val settingsFileField = TextFieldWithBrowseButton()
        settingsFileField.text = settings.settingsFilePath
        settingsFileField.preferredSize = JBUI.size(500, 36)
        val settingsBorderColor = if (UIUtil.isUnderIntelliJLaF()) {
            JBColor.namedColor("TextField.borderColor", JBColor.border())
        } else {
            JBColor.border()
        }
        settingsFileField.textField.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(settingsBorderColor, 1),
            JBUI.Borders.empty(6, 10)
        )
        settingsFileField.textField.background = UIUtil.getTextFieldBackground()
        settingsFileField.textField.foreground = UIUtil.getTextFieldForeground()
        settingsFileTextField = settingsFileField
        
        // File chooser for TOML files
        val tomlFileChooser = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor("toml")
        tomlFileChooser.title = "Select RSCM Settings File"
        tomlFileChooser.description = "Choose a TOML settings file (e.g., rscm-settings.toml)"
        settingsFileField.addActionListener {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(tomlFileChooser, project, null)?.let { file ->
                settingsFileField.text = file.path
                updateUIForSettingsFile()
            }
        }
        
        // Add listener to update UI when settings file changes
        settingsFileField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                updateUIForSettingsFile()
                reloadMappings() // Reload when settings file changes
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                updateUIForSettingsFile()
                reloadMappings() // Reload when settings file changes
            }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                updateUIForSettingsFile()
                reloadMappings() // Reload when settings file changes
            }
        })
        
        val settingsFilePanel = JPanel(BorderLayout())
        settingsFilePanel.border = JBUI.Borders.empty(8, 0)
        settingsFilePanel.add(settingsFileField, BorderLayout.CENTER)
        
        settingsFileSection.add(settingsFilePanel, BorderLayout.CENTER)
        contentPanel.add(settingsFileSection)

        // Section: Mappings Directories with add button in header
        val directoriesSection = createSectionPanelWithAction(
            "Mappings Directories",
            "Add directories containing .rscm, .dat, and .toml mapping files"
        ) {
            addMappingPathRow("")
            mappingsPathsPanel?.revalidate()
            mappingsPathsPanel?.repaint()
        }
        
        // Override warning panel (will be shown/hidden based on settings file)
        // This will be placed below the title and description
        val overrideWarningPanel = JPanel(BorderLayout())
        overrideWarningPanel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(8)
        )
        overrideWarningPanel.background = UIUtil.getToolTipBackground()
        overrideWarningPanel.isVisible = false // Hidden by default
        
        val overrideLabel = JBLabel()
        overrideLabel.foreground = java.awt.Color.WHITE
        overrideLabel.border = JBUI.Borders.empty(4, 8)
        overrideWarningPanel.add(overrideLabel, BorderLayout.CENTER)
        
        // Store reference to override panel for later updates
        directoriesSection.putClientProperty("overrideWarningPanel", overrideWarningPanel)
        directoriesSection.putClientProperty("overrideLabel", overrideLabel)
        
        // Get the header container and add warning below description
        val headerContainer = directoriesSection.getComponent(0) as? JPanel
        headerContainer?.add(overrideWarningPanel, BorderLayout.CENTER)
        
        // Create a scrollable panel for the directory list
        val pathsPanel = JPanel(VerticalLayout(JBUI.scale(8)))
        pathsPanel.border = JBUI.Borders.empty(8)
        mappingsPathsPanel = pathsPanel
        pathsGridBag = GridBag().setDefaultInsets(JBUI.insets(3)).setDefaultAnchor(GridBagConstraints.WEST)
        
        // Load existing paths from effective settings
        val effectiveSettings = settings.getEffectiveSettings()
        val existingPaths = effectiveSettings.mappingsPaths
        if (existingPaths.isEmpty()) {
            // Add one empty row by default
            addMappingPathRow("")
        } else {
            for (path in existingPaths) {
                addMappingPathRow(path)
            }
        }
        
        // Ensure info panel is at the end after loading paths
        ensureInfoPanelAtEnd()
        
        // Create scroll pane for the paths panel
        val scrollPane = JBScrollPane(pathsPanel)
        scrollPane.preferredSize = JBUI.size(600, 250)
        scrollPane.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(8)
        )
        
        directoriesSection.add(scrollPane, BorderLayout.CENTER)
        contentPanel.add(directoriesSection)

        // Note: Referential Mappings section removed from UI but code kept for future use
        // The referentialMappingsTextField is still initialized in reset() method

        // Add info note below the last directory entry (inside the scrollable panel)
        val infoPanel = JPanel(BorderLayout())
        infoPanel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(8)
        )
        infoPanel.background = UIUtil.getToolTipBackground()
        val infoLabel = JBLabel("<html><b>Note:</b> Mappings will be automatically reloaded when directories are added or removed.</html>")
        infoLabel.border = JBUI.Borders.empty(4, 8)
        infoPanel.add(infoLabel, BorderLayout.CENTER)
        
        // Store reference to info panel for updates
        pathsPanel.putClientProperty("infoPanel", infoPanel)
        
        // Add info panel to paths panel (will always be at the end)
        pathsPanel.add(infoPanel)

        mappingsPanel.add(contentPanel, BorderLayout.NORTH)
        mappingsPanel.add(Box.createVerticalGlue(), BorderLayout.CENTER)

        return mappingsPanel
    }
    
    /**
     * Update UI state based on whether a settings file is configured.
     */
    private fun updateUIForSettingsFile() {
        val settingsFile = settingsFileTextField?.text?.trim() ?: ""
        val hasSettingsFile = settingsFile.isNotEmpty() && java.io.File(settingsFile).exists()
        
        // Disable/enable providers tab
        val providersTabIndex = 1
        tabbedPane?.setEnabledAt(providersTabIndex, !hasSettingsFile)
        
        // Find the directories section and update override warning
        mappingsContentPanel?.let { panel ->
            val directoriesSection = panel.components.find { 
                it is JPanel && it.getClientProperty("overrideWarningPanel") != null
            } as? JPanel
            
            directoriesSection?.let { section ->
                val overrideWarningPanel = section.getClientProperty("overrideWarningPanel") as? JPanel
                val overrideLabel = section.getClientProperty("overrideLabel") as? JBLabel
                
                if (hasSettingsFile) {
                    // Show override warning with white text
                    overrideWarningPanel?.isVisible = true
                    overrideLabel?.text = "<html><b>⚠ OVERRIDDEN BY SETTINGS FILE</b><br>Settings are loaded from: <code>$settingsFile</code></html>"
                    overrideLabel?.foreground = java.awt.Color.WHITE
                    
                    // Disable all mapping controls
                    mappingsPathsList.forEach { it.isEnabled = false }
                    addButton?.isEnabled = false
                    referentialMappingsTextField?.isEnabled = false
                } else {
                    // Hide override warning
                    overrideWarningPanel?.isVisible = false
                    
                    // Enable all mapping controls
                    mappingsPathsList.forEach { it.isEnabled = true }
                    updateAddButtonState()
                    referentialMappingsTextField?.isEnabled = true
                }
            }
        }
        
        mappingsContentPanel?.revalidate()
        mappingsContentPanel?.repaint()
    }
    
    /**
     * Create a modern section panel with title and description.
     */
    private fun createSectionPanel(title: String, description: String): JPanel {
        val section = JPanel(BorderLayout())
        section.border = JBUI.Borders.empty(0, 0, 12, 0)
        
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = JBUI.Borders.empty(0, 0, 8, 0)
        
        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(java.awt.Font.BOLD, titleLabel.font.size + 1f)
        headerPanel.add(titleLabel, BorderLayout.NORTH)
        
        val descLabel = JBLabel(description)
        descLabel.foreground = UIUtil.getLabelDisabledForeground()
        descLabel.border = JBUI.Borders.empty(4, 0, 0, 0)
        headerPanel.add(descLabel, BorderLayout.CENTER)
        
        section.add(headerPanel, BorderLayout.NORTH)
        return section
    }
    
    /**
     * Create a section panel with an action button in the header.
     */
    private fun createSectionPanelWithAction(title: String, description: String, action: () -> Unit): JPanel {
        val section = JPanel(BorderLayout())
        section.border = JBUI.Borders.empty(0, 0, 12, 0)
        
        // Create header container that can hold title, description, and warning
        val headerContainer = JPanel(BorderLayout())
        headerContainer.isOpaque = false
        
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = JBUI.Borders.empty(0, 0, 8, 0)
        
        val titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = false
        
        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(java.awt.Font.BOLD, titleLabel.font.size + 1f)
        titlePanel.add(titleLabel, BorderLayout.WEST)
        
        // Add button next to title - only enable if no empty directories exist
        addButton = createIconButton(AllIcons.General.Add, "Add another mappings directory")
        addButton?.addActionListener {
            // Check if there's already an empty directory
            val hasEmpty = mappingsPathsList.any { it.text.isBlank() }
            if (!hasEmpty) {
                action()
            }
        }
        updateAddButtonState()
        val buttonWrapper = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        buttonWrapper.isOpaque = false
        buttonWrapper.add(addButton)
        titlePanel.add(buttonWrapper, BorderLayout.EAST)
        
        headerPanel.add(titlePanel, BorderLayout.NORTH)
        
        val descLabel = JBLabel(description)
        descLabel.foreground = UIUtil.getLabelDisabledForeground()
        descLabel.border = JBUI.Borders.empty(4, 0, 0, 0)
        headerPanel.add(descLabel, BorderLayout.CENTER)
        
        headerContainer.add(headerPanel, BorderLayout.NORTH)
        // Warning panel will be added to BorderLayout.CENTER of headerContainer later
        
        section.add(headerContainer, BorderLayout.NORTH)
        return section
    }
    
    /**
     * Create a modern icon button.
     */
    private fun createIconButton(icon: javax.swing.Icon, tooltip: String): JButton {
        val button = JButton(icon)
        button.toolTipText = tooltip
        button.preferredSize = JBUI.size(32, 32)
        button.isContentAreaFilled = false
        button.isFocusPainted = false
        button.border = JBUI.Borders.empty(4)
        button.isOpaque = false
        return button
    }
    
    /**
     * Update the add button state based on whether there are empty directories.
     */
    private fun updateAddButtonState() {
        val hasEmpty = mappingsPathsList.any { it.text.isBlank() }
        addButton?.isEnabled = !hasEmpty
        if (hasEmpty) {
            addButton?.toolTipText = "Please fill in the empty directory field first"
        } else {
            addButton?.toolTipText = "Add another mappings directory"
        }
    }
    
    
    /**
     * Create the general settings panel (referential mappings).
     */
    private fun createGeneralSettingsPanel(): JPanel {
        val generalPanel = JPanel(GridBagLayout())
        val gridBag = GridBag().setDefaultInsets(JBUI.insets(5)).setDefaultAnchor(GridBagConstraints.WEST)

        val refMappingLabel = JLabel("Mappings which reference other mappings (e.g. component=interface,dbrow=dbtable):")
        generalPanel.add(refMappingLabel, gridBag.nextLine().next().weightx(0.0))
        
        referentialMappingsTextField = TextFieldWithHistory()
        referentialMappingsTextField?.preferredSize = JBUI.size(400, 30)
        generalPanel.add(referentialMappingsTextField!!, gridBag.next().weightx(1.0).fillCellHorizontally())

        // Force the components to align at the top left by filling vertical and horizontal space
        gridBag.nextLine()
        gridBag.weighty(1.0) // Push components to the top by giving remaining vertical space
        generalPanel.add(Box.createVerticalGlue(), gridBag)

        return generalPanel
    }
    
    /**
     * Add a new row for a mappings directory path.
     */
    private fun addMappingPathRow(initialPath: String): TextFieldWithBrowseButton {
        val rowPanel = JPanel(BorderLayout())
        rowPanel.border = JBUI.Borders.empty(2, 0)
        
        val textField = TextFieldWithBrowseButton()
        textField.text = initialPath
        textField.preferredSize = JBUI.size(500, 36)
        // Improve text field styling with theme-aware border
        val borderColor = if (UIUtil.isUnderIntelliJLaF()) {
            // Use a more visible border color that adapts to theme
            JBColor.namedColor("TextField.borderColor", JBColor.border())
        } else {
            JBColor.border()
        }
        textField.textField.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(borderColor, 1),
            JBUI.Borders.empty(6, 10)
        )
        textField.textField.background = UIUtil.getTextFieldBackground()
        textField.textField.foreground = UIUtil.getTextFieldForeground()

        // Create a file chooser descriptor to allow only directory selection
        val fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        fileChooserDescriptor.title = "Select Mappings Directory"

        // Add action listener to open the file chooser dialog when the button is clicked
        textField.addBrowseFolderListener(
            project,
            fileChooserDescriptor
                .withTitle("Select Directory")
                .withDescription("Choose a RSCM mappings directory")
        )
        
        // Update add button state when text changes
        textField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                updateAddButtonState()
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                updateAddButtonState()
            }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                updateAddButtonState()
            }
        })
        
        // Remove button with trash/delete icon
        val removeButton = createIconButton(
            AllIcons.General.Remove, // Trash/delete icon
            "Remove this directory"
        )
        removeButton.addActionListener {
            mappingsPathsList.remove(textField)
            mappingsPathsPanel?.remove(rowPanel)
            updateAddButtonState()
            // Ensure info panel is still at the end
            ensureInfoPanelAtEnd()
            mappingsPathsPanel?.revalidate()
            mappingsPathsPanel?.repaint()
        }
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        buttonPanel.isOpaque = false
        buttonPanel.border = JBUI.Borders.emptyLeft(8)
        buttonPanel.add(removeButton)
        
        rowPanel.add(textField, BorderLayout.CENTER)
        rowPanel.add(buttonPanel, BorderLayout.EAST)
        
        mappingsPathsList.add(textField)
        mappingsPathsPanel?.add(rowPanel)
        
        // Ensure info panel is always at the end
        ensureInfoPanelAtEnd()
        
        return textField
    }
    
    /**
     * Ensure the info panel is always at the end of the paths panel.
     */
    private fun ensureInfoPanelAtEnd() {
        val panel = mappingsPathsPanel ?: return
        val infoPanel = panel.getClientProperty("infoPanel") as? JPanel ?: return
        
        // Remove info panel if it exists
        panel.remove(infoPanel)
        // Add it back at the end
        panel.add(infoPanel)
    }
    
    /**
     * Create the providers settings panel.
     */
    private fun createProvidersPanel(): JPanel {
        val providersPanel = JPanel(BorderLayout())
        providersPanel.border = JBUI.Borders.empty(15)

        val contentPanel = JPanel(VerticalLayout(JBUI.scale(16)))
        contentPanel.border = EmptyBorder(0, 0, 0, 0)

        // Header
        val headerLabel = JBLabel("Enable or disable RSCM providers")
        headerLabel.font = headerLabel.font.deriveFont(java.awt.Font.BOLD, headerLabel.font.size + 2f)
        headerLabel.border = JBUI.Borders.empty(0, 0, 12, 0)
        contentPanel.add(headerLabel)

        // File Provider Card
        val fileProviderCard = createProviderCard(
            "File Provider",
            "Loads properties from .rscm files in the mappings directories",
            settings.enableFileProvider
        ) { enabled ->
            enableFileProviderCheckBox?.isSelected = enabled
        }
        enableFileProviderCheckBox = fileProviderCard.second
        contentPanel.add(fileProviderCard.first)

        // Alter Constant Provider Card
        val alterProviderCard = createProviderCard(
            "Alter Constant Provider",
            "Loads properties from .dat and .toml files (gamevals.toml) in the mappings directories",
            settings.enableAlterConstantProvider
        ) { enabled ->
            enableAlterConstantProviderCheckBox?.isSelected = enabled
        }
        enableAlterConstantProviderCheckBox = alterProviderCard.second
        contentPanel.add(alterProviderCard.first)

        providersPanel.add(contentPanel, BorderLayout.NORTH)
        providersPanel.add(Box.createVerticalGlue(), BorderLayout.CENTER)

        return providersPanel
    }
    
    /**
     * Create a modern provider card with checkbox and description.
     */
    private fun createProviderCard(
        title: String,
        description: String,
        initialValue: Boolean,
        onToggle: (Boolean) -> Unit
    ): Pair<JPanel, JBCheckBox> {
        val card = JPanel(BorderLayout())
        card.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(12)
        )
        card.background = UIUtil.getPanelBackground()
        
        val contentPanel = JPanel(BorderLayout())
        contentPanel.isOpaque = false
        
        val headerPanel = JPanel(BorderLayout())
        headerPanel.isOpaque = false
        
        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(java.awt.Font.BOLD)
        headerPanel.add(titleLabel, BorderLayout.WEST)
        
        val checkBox = JBCheckBox("", initialValue)
        checkBox.addActionListener { onToggle(checkBox.isSelected) }
        headerPanel.add(checkBox, BorderLayout.EAST)
        
        contentPanel.add(headerPanel, BorderLayout.NORTH)
        
        val descLabel = JBLabel(description)
        descLabel.foreground = UIUtil.getLabelDisabledForeground()
        descLabel.border = JBUI.Borders.empty(4, 0, 0, 0)
        contentPanel.add(descLabel, BorderLayout.CENTER)
        
        card.add(contentPanel, BorderLayout.CENTER)
        
        return Pair(card, checkBox)
    }

    override fun isModified(): Boolean {
        val currentPaths = mappingsPathsList.map { it.text }.filter { it.isNotEmpty() }
        return settingsFileTextField?.text != settings.settingsFilePath ||
                currentPaths != settings.mappingsPaths ||
                referentialMappingsTextField?.text != settings.referentialMappings ||
                enableFileProviderCheckBox?.isSelected != settings.enableFileProvider ||
                enableAlterConstantProviderCheckBox?.isSelected != settings.enableAlterConstantProvider
    }

    override fun apply() {
        val settingsFile = settingsFileTextField?.text?.trim() ?: ""
        val oldSettingsFile = settings.settingsFilePath
        settings.settingsFilePath = settingsFile
        
        // If using settings file, load from it
        if (settingsFile.isNotEmpty()) {
            val parsed = RSCMSettingsFileParser.parseSettingsFile(settingsFile)
            if (parsed != null) {
                // Settings file overrides everything
                settings.mappingsPaths = parsed.mappingsDirectories.toMutableList()
                settings.enableFileProvider = parsed.enableFileProvider
                settings.enableAlterConstantProvider = parsed.enableAlterConstantProvider
                settings.referentialMappings = parsed.referentialMappings
            }
        } else {
            // Use UI values
            val paths = mappingsPathsList.map { it.text }.filter { it.isNotEmpty() }
            settings.mappingsPaths = paths.toMutableList()
        settings.referentialMappings = referentialMappingsTextField?.text.orEmpty()
            settings.enableFileProvider = enableFileProviderCheckBox?.isSelected ?: true
            settings.enableAlterConstantProvider = enableAlterConstantProviderCheckBox?.isSelected ?: true
        }
        
        // Update UI state
        updateUIForSettingsFile()
        
        // Always reload mappings when settings are saved
        reloadMappings()
    }
    
    /**
     * Reload mappings from all providers when directories change.
     */
    private fun reloadMappings() {
        // Reload Alter Constant Provider
        try {
            dev.openrune.language.AlterConstantProvider.getInstance(project).reloadMappings()
        } catch (e: Exception) {
            // Provider might not be initialized yet, that's okay
        }
        
        // File Provider doesn't need explicit reload - it reads from file system on demand
        // But we can trigger a refresh of the Virtual File System to ensure file changes are detected
        com.intellij.openapi.vfs.VirtualFileManager.getInstance().refreshWithoutFileWatcher(false)
    }

    override fun reset() {
        // Reset settings file field
        settingsFileTextField?.text = settings.settingsFilePath
        
        // Get effective settings (from file or UI)
        val effectiveSettings = settings.getEffectiveSettings()
        
        // Clear existing rows
        mappingsPathsList.clear()
        mappingsPathsPanel?.removeAll()
        
        // Rebuild from effective settings
        val existingPaths = effectiveSettings.mappingsPaths
        if (existingPaths.isEmpty()) {
            addMappingPathRow("")
        } else {
            for (path in existingPaths) {
                addMappingPathRow(path)
            }
        }
        
        mappingsPathsPanel?.revalidate()
        mappingsPathsPanel?.repaint()
        
        // Initialize referential mappings field (hidden from UI but kept in code)
        if (referentialMappingsTextField == null) {
            referentialMappingsTextField = TextFieldWithHistory()
        }
        referentialMappingsTextField?.text = effectiveSettings.referentialMappings
        enableFileProviderCheckBox?.isSelected = effectiveSettings.enableFileProvider
        enableAlterConstantProviderCheckBox?.isSelected = effectiveSettings.enableAlterConstantProvider
        
        // Update UI state
        updateUIForSettingsFile()
    }

    override fun getDisplayName(): String = "RSCM Settings"
}
