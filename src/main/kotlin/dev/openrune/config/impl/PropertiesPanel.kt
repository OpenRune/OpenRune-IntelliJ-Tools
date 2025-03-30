package dev.openrune.config.impl

import dev.openrune.config.Section
import com.intellij.ui.components.JBPanel
import com.intellij.ui.treeStructure.Tree
import dev.openrune.definition.Definition
import java.awt.BorderLayout
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.reflect.full.memberProperties

class PropertiesPanel : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Properties")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private var currentSection: Section? = null
    private var defaultValue : Definition? = null


    var selected : String = ""

    init {
        add(tree, BorderLayout.CENTER)

        tree.addTreeSelectionListener { event ->
            val selectedNode = event.path.lastPathComponent as? DefaultMutableTreeNode
            val selectedFieldName = selectedNode?.userObject as? String

            if (selectedFieldName != null && selectedFieldName != "Properties" && currentSection != null) {
                val property = currentSection!!.def::class.memberProperties.find { it.name == selectedFieldName }
                if (property != null) {
                    val typeName = property.returnType.toString().replace("kotlin.", "")
                    selected = convertValue(selectedFieldName,typeName)
                }
            }
        }
    }

    private fun convertValue(selectedFieldName : String, typeName : String) : String {

        val value = defaultValue!!::class.memberProperties.find { it.name == selectedFieldName }
        val prop = value?.getter?.call(defaultValue).toString()

        val outputValue = when(typeName) {
            "dev.openrune.definition.serialization.Rscm /* = Int */" -> "0"
            "Boolean" -> !prop.toBoolean()
            "String" -> "STRING HERE"
            "dev.openrune.definition.serialization.ListRscm? /* = collections.MutableList<Int>? */" -> "[ 0 , 0 ]"
            "collections.MutableList<Int>?" -> "[ 0 , 0 ]"
            "Int" -> (prop.toInt() + 1)
            else -> 0
        }

        if (typeName.contains("map")) {
            val mapState = buildString {
                appendLine("[${currentSection?.type?.header}.${selectedFieldName}]")
                appendLine()
                appendLine("    0 = \"VALUE HERE\"")
            }
            return  mapState
        } else {
            return  "$selectedFieldName =  $outputValue"
        }
    }

    fun update(section: Section?) {
        rootNode.removeAllChildren()
        if (section != null) {
            currentSection = section
            defaultValue = currentSection!!.codec.createDefinition()
            section.def::class.memberProperties.forEach { property ->
                println(property.name)
                if (property.name != "id" || property.name != "extra") {
                    val typeName = property.returnType.toString().replace("kotlin.", "")
                    val node = DefaultMutableTreeNode(property.name)
                    rootNode.add(node)
                }
            }
            treeModel.reload()
        }
    }
}