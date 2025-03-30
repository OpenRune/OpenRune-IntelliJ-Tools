package dev.openrune.config

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp

class ConfigFileEditorProvider : WeighedFileEditorProvider() {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        val fileType = file.fileType
        return (fileType.defaultExtension == "toml" ||
                (fileType.defaultExtension == "" && file.name.endsWith(".toml"))) && JBCefApp.isSupported()
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return MarkdownPreviewFileEditor(project, file)
    }

    override fun getEditorTypeId(): String {
        return "Config Editor"
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
    }

}
