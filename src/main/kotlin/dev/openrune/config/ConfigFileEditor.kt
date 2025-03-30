package dev.openrune.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import dev.openrune.config.impl.ConfirmRemoval
import dev.openrune.config.impl.CreateConfigDialog
import dev.openrune.config.impl.DetailsPanel
import dev.openrune.config.impl.PropertiesPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import dev.openrune.definition.DefinitionCodec
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import java.awt.*
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.tree.*
import kotlin.reflect.full.memberProperties

class MarkdownPreviewFileEditor(private val myProject: Project, private val myFile: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val detailsPanel = DetailsPanel()

    private val createConfig = CreateConfigDialog { selectedType, enteredId ->
        val document = myFile.findDocument()!!
        WriteCommandAction.runWriteCommandAction(myProject) {
            val text = buildString {
                appendLine()
                appendLine("[[${selectedType.header}]]")
                appendLine("id = $enteredId")
            }
            document.insertString(document.textLength, text)
        }
    }

    private val myDocument = FileDocumentManager.getInstance().getDocument(myFile)
    private val myPanel = JPanel(BorderLayout())
    private var propertiesPanel = PropertiesPanel()
    lateinit var itemTree: Tree
    private val root = DefaultMutableTreeNode("Configs")
    private var documentModified = false
    private var sections: MutableMap<String, List<Section>> = mutableMapOf()

    init {
        myDocument?.text?.let { findSections(it) }

        myDocument?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                findSections(myDocument.text)
                updateTree()
            }
        })

        setupUI()
    }

    @OptIn(InternalSerializationApi::class)
    private fun findSections(text: String) {
        sections.clear()
        val sectionData = mutableListOf<Section>()
        val regex = Regex("\\[\\[(.*?)]]\\s*(?:\\r?\\n|\\r)(.*?)(?=(\\[\\[.*?]]|\\z))", RegexOption.DOT_MATCHES_ALL)

        regex.findAll(text).forEach { match ->
            val type = match.groupValues[1]
            val content = match.groupValues[2].trim()
            val startLine = text.substring(0, match.range.first).count { it == '\n' } + 1
            val endLine = text.substring(0, match.range.last).count { it == '\n' } + 1

            val configType = ConfigType.forHeader(type)!!
            val constructor = configType.codec.constructors.first()
            val codecInstance = if (constructor.parameters.isEmpty()) {
                constructor.call() as DefinitionCodec<*>
            } else {
                constructor.call(229) as DefinitionCodec<*>
            }

            val defaultDef = codecInstance.createDefinition()
            val config = Toml(TomlInputConfig(ignoreUnknownNames = true)).decodeFromString(defaultDef::class.serializer(), content)
            sectionData.add(Section(configType, codecInstance, config, content, startLine..endLine))
        }

        sections = sectionData.groupBy { it.type.header }.toMutableMap()
    }

    private fun updateTree() {
        SwingUtilities.invokeLater {
            val root = DefaultMutableTreeNode("Configs")

            val expandedRows = mutableListOf<Int>()
            for (i in 0 until itemTree.rowCount) {
                if (itemTree.isExpanded(i)) {
                    expandedRows.add(i)
                }
            }

            val selectedNode = itemTree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
            val selectedNodeName = selectedNode?.toString()

            sections.forEach { (type, itemList) ->
                val typeNode = DefaultMutableTreeNode(type)
                itemList.forEach { item ->
                    val name = item.def::class.memberProperties.find { it.name == "name" }?.getter?.call(item.def) as? String ?: ""
                    val node = DefaultMutableTreeNode("${item.def.id} ${if (name == "null") "" else ": $name"}")
                    item.treeNode = node
                    typeNode.add(node)
                }
                root.add(typeNode)
            }
            val newModel = DefaultTreeModel(root)
            itemTree.model = newModel
            newModel.reload()

            for (i in expandedRows) {
                if (i < itemTree.rowCount) {
                    itemTree.expandRow(i)
                }
            }

            if (selectedNodeName != null) {
                for (i in 0 until itemTree.rowCount) {
                    val path = itemTree.getPathForRow(i)
                    val node = path.lastPathComponent as? DefaultMutableTreeNode
                    if (node?.toString() == selectedNodeName) {
                        itemTree.selectionPath = path
                        break
                    }
                }
            }


            propertiesPanel.update(getSelectedSection())
            myPanel.revalidate()
            myPanel.repaint()
            documentModified = true
        }
    }

    private fun expandAllNodes(tree: JTree, rowCount: Int) {
        var count = rowCount
        var i = 0
        while (i < count) {
            tree.expandRow(i)
            i++
            count = tree.rowCount
        }
    }

    private fun collapseAllNodes(tree: JTree, startingIndex: Int, rowCount: Int) {
        for (i in rowCount - 1 downTo startingIndex) {
            tree.collapseRow(i)
        }
    }

    private fun getSelectedSection(): Section? {
        val selectedNode = itemTree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        selectedNode?.let {
            sections.values.flatten().forEach { section ->
                if (section.treeNode == selectedNode) {
                    return section
                }
            }
        }
        return null
    }

    private fun setupUI() {
        itemTree = Tree(DefaultTreeModel(root)).apply {
            isRootVisible = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            addTreeSelectionListener { event ->
                val selectedNode = event.path.lastPathComponent as? DefaultMutableTreeNode
                selectedNode?.let {
                    sections.values.flatten().forEach { section ->
                        if (section.treeNode == selectedNode) {
                            detailsPanel.update(section)
                            return@forEach
                        }
                    }
                }
            }
        }

        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(0, 30)
            val headerLabel = JBLabel("Configs")
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
            val headerContent = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                val gbc = GridBagConstraints().apply {
                    gridy = 0
                    weighty = 1.0
                    anchor = GridBagConstraints.CENTER
                    insets = JBUI.insets(0, 10)
                }

                gbc.gridx = 0
                gbc.weightx = 1.0
                gbc.anchor = GridBagConstraints.WEST
                add(headerLabel, gbc)

                val buttonsPanel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                    val buttonGbc = GridBagConstraints().apply {
                        gridy = 0
                        weighty = 1.0
                        anchor = GridBagConstraints.CENTER
                        insets = JBUI.insets(0, 5)
                    }

                    buttonGbc.gridx = 0
                    add(createActionButton("Add", AllIcons.Actions.AddFile) { createConfig.showAndGet() }, buttonGbc)

                    buttonGbc.gridx = 1
                    add(createActionButton("Remove", AllIcons.Actions.GC) {
                        if (ConfirmRemoval().showAndGet()) {
                            getSelectedSection()?.let { section ->
                                val document = myFile.findDocument() ?: return@let
                                WriteCommandAction.runWriteCommandAction(myProject) {
                                    val startOffset = document.getLineStartOffset(section.lineRange.first - 1)
                                    val endOffset = document.getLineEndOffset(section.lineRange.last - 1)
                                    document.deleteString(startOffset, endOffset)
                                }
                            }
                        }
                    }, buttonGbc)

                    buttonGbc.gridx = 2
                    add(createActionButton("Refresh", AllIcons.Actions.Refresh) {
                        findSections(myDocument!!.text)
                        updateTree()
                    }, buttonGbc)

                    buttonGbc.gridx = 3
                    add(createActionButton("Expand All", AllIcons.Actions.Expandall) {
                        expandAllNodes(itemTree, itemTree.rowCount)
                    }, buttonGbc)

                    buttonGbc.gridx = 4
                    add(createActionButton("Collapse All", AllIcons.Actions.Collapseall) {
                        collapseAllNodes(itemTree, 0, itemTree.rowCount)
                    }, buttonGbc)
                }

                gbc.gridx = 1
                gbc.weightx = 0.0
                gbc.anchor = GridBagConstraints.EAST
                add(buttonsPanel, gbc)
            }

            add(headerContent, BorderLayout.CENTER)
        }

        val header2 = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(0, 30)
            val headerLabel = JBLabel("Properties")
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)
            val headerContent = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                val gbc = GridBagConstraints().apply {
                    gridy = 0
                    weighty = 1.0
                    anchor = GridBagConstraints.CENTER
                    insets = JBUI.insets(0, 10)
                }

                gbc.gridx = 0
                gbc.weightx = 1.0
                gbc.anchor = GridBagConstraints.WEST
                add(headerLabel, gbc)

                val buttonsPanel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                    val buttonGbc = GridBagConstraints().apply {
                        gridy = 0
                        weighty = 1.0
                        anchor = GridBagConstraints.CENTER
                        insets = JBUI.insets(0, 5)
                    }

                    buttonGbc.gridx = 0
                    add(createActionButton("Add", AllIcons.Actions.AddFile) {
                        if (propertiesPanel.selected != null) {
                            WriteCommandAction.runWriteCommandAction(myProject) {
                                val section = getSelectedSection() ?: return@runWriteCommandAction
                                val document = myFile.findDocument() ?: return@runWriteCommandAction

                                val lineRange = section.lineRange
                                var insertOffset: Int? = null

                                for (line in lineRange.first..lineRange.last) {
                                    val startOffset = document.getLineStartOffset(line - 1)
                                    val endOffset = document.getLineEndOffset(line - 1)
                                    val text = document.getText(TextRange(startOffset, endOffset)).trim()

                                    if (text.isEmpty()) {
                                        insertOffset = startOffset
                                        break
                                    }
                                }

                                val finalInsertOffset = insertOffset ?: (document.getLineEndOffset(lineRange.last - 1) + 1)

                                document.insertString(finalInsertOffset, "${propertiesPanel.selected}\n")
                            }
                        }
                    }, buttonGbc)

                    buttonGbc.gridx = 1
                    add(createActionButton("Remove", AllIcons.Actions.GC) {}, buttonGbc)
                }

                gbc.gridx = 1
                gbc.weightx = 0.0
                gbc.anchor = GridBagConstraints.EAST
                add(buttonsPanel, gbc)
            }

            add(headerContent, BorderLayout.CENTER)
        }

        val treeContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(header, BorderLayout.NORTH) // Fixed Header
            add(JBScrollPane(itemTree), BorderLayout.CENTER) // Scrollable Tree
        }

        val propertiesContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(header2, BorderLayout.NORTH)
            add(JBScrollPane(propertiesPanel), BorderLayout.CENTER)
        }

        val splitPane2 = JBSplitter(true, 0.35f).apply {
            firstComponent = treeContainer
            secondComponent = propertiesContainer
            dividerWidth = 1
        }

        val treePanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1)
            add(header, BorderLayout.NORTH)
            add(splitPane2, BorderLayout.CENTER)
        }

        val detailsPanelContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(detailsPanel, BorderLayout.NORTH)
        }


        val splitPane = JBSplitter(false, 0.15f).apply {
            firstComponent = treePanel
            secondComponent = detailsPanelContainer
            dividerWidth = 1
        }


        updateTree()

        myPanel.add(splitPane)
    }

    private fun createActionButton(text: String, icon: Icon, action: () -> Unit): ActionButton {
        val action = object : AnAction(text) {
            override fun actionPerformed(e: AnActionEvent) {
                action.invoke()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }
        }

        val presentation = Presentation(text).apply { this.icon = icon }
        val actionButton = ActionButton(action, presentation, "MyFileEditor", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
        actionButton.preferredSize = Dimension(100, 30)

        return actionButton
    }


    override fun getComponent(): JComponent = myPanel
    override fun getPreferredFocusedComponent(): JComponent = myPanel
    override fun getName(): String = "Config Editor"
    override fun isModified(): Boolean = documentModified
    override fun isValid(): Boolean = true
    override fun dispose() {}
    override fun getFile(): VirtualFile = myFile
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getStructureViewBuilder(): StructureViewBuilder? = null
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun setState(state: FileEditorState) {}
}