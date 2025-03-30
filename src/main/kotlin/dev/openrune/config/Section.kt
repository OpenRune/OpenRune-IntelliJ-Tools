package dev.openrune.config

import dev.openrune.definition.Definition
import dev.openrune.definition.DefinitionCodec
import javax.swing.tree.DefaultMutableTreeNode

data class Section(
    val type: ConfigType,
    val codec: DefinitionCodec<out Definition>,
    val def: Definition,
    val content: String,
    val lineRange: IntRange,
    var treeNode: DefaultMutableTreeNode? = null
)