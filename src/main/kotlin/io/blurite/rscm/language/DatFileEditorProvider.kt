package io.blurite.rscm.language

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.blurite.rscm.settings.RSCMProjectSettings
import org.jdom.Element
import java.nio.file.Paths

/**
 * Provider for custom .dat file editor that replaces the code view.
 * Only used for .dat files that are loaded by the AlterConstantProvider.
 */
class DatFileEditorProvider : FileEditorProvider, DumbAware {
    
    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.extension != "dat") return false
        
        val settings = RSCMProjectSettings.getInstance(project)
        val mappingDirectories = settings.getEffectiveSettings().mappingsPaths.filter { it.isNotEmpty() }
        if (mappingDirectories.isEmpty()) return false
        
        // Get normalized file path
        val filePath = try {
            file.toNioPath()?.toAbsolutePath()?.normalize()
                ?: Paths.get(file.path).toAbsolutePath().normalize()
        } catch (e: Exception) {
            return false
        }
        
        // Check if file is in any mappings directory
        return mappingDirectories.any { dir ->
            try {
                filePath.startsWith(Paths.get(dir).toAbsolutePath().normalize())
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return DatFileEditor(project, file)
    }
    
    override fun getEditorTypeId(): String = "dat-file-editor"
    
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    
    override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
        return FileEditorState.INSTANCE
    }
    
    override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
        // No state to write
    }
}

