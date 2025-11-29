package dev.openrune.language

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import io.blurite.rscm.language.RSCMReference
import io.blurite.rscm.language.psi.RSCMProperty

/**
 * GotoDeclarationHandler that intercepts navigation for Alter constant-based properties.
 * When navigating to an Alter constant property, shows a dialog with all properties instead of opening a file.
 * 
 * @author Auto-generated
 */
class AlterConstantGotoDeclarationHandler : GotoDeclarationHandler {
    
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        
        val project = sourceElement.project
        
        // Get all references from the element
        val references = sourceElement.references
        for (reference in references) {
            // Check if this is an RSCMReference
            if (reference is RSCMReference) {
                // Check multi-resolve for poly-variant references (RSCMReference is poly-variant)
                val results = reference.multiResolve(false)
                for (result in results) {
                    val element = result.element
                    if (element is RSCMProperty) {
                        val containingFile = element.containingFile
                        val fileName = containingFile.name
                        
                        // Check if this is an Alter constant-based property (from temp file)
                        if (fileName.startsWith("temp_") && fileName.endsWith(".rscm")) {
                            // Extract prefix from filename (e.g., "temp_items.rscm" -> "items")
                            val prefix = fileName.removePrefix("temp_").removeSuffix(".rscm")
                            
                            // Get the Alter constant data
                            val alterData = getAlterDataForPrefix(project, prefix)
                            
                            if (alterData != null) {
                                // Show dialog immediately (synchronously to prevent default navigation)
                                val dialog = AlterConstantPropertiesDialog(prefix, alterData)
                                dialog.show()
                                // Return empty array to prevent default file navigation
                                return PsiElement.EMPTY_ARRAY
                            }
                        }
                    }
                }
            } else if (reference is PsiPolyVariantReference) {
                // Check other poly-variant references
                val results = reference.multiResolve(false)
                for (result in results) {
                    val element = result.element
                    if (element is RSCMProperty) {
                        val containingFile = element.containingFile
                        val fileName = containingFile.name
                        
                        if (fileName.startsWith("temp_") && fileName.endsWith(".rscm")) {
                            val prefix = fileName.removePrefix("temp_").removeSuffix(".rscm")
                            val alterData = getAlterDataForPrefix(project, prefix)
                            
                            if (alterData != null) {
                                val dialog = AlterConstantPropertiesDialog(prefix, alterData)
                                dialog.show()
                                return PsiElement.EMPTY_ARRAY
                            }
                        }
                    }
                }
            } else {
                // Try single resolve
                val resolved = reference.resolve()
                if (resolved is RSCMProperty) {
                    val containingFile = resolved.containingFile
                    val fileName = containingFile.name
                    
                    if (fileName.startsWith("temp_") && fileName.endsWith(".rscm")) {
                        val prefix = fileName.removePrefix("temp_").removeSuffix(".rscm")
                        val alterData = getAlterDataForPrefix(project, prefix)
                        
                        if (alterData != null) {
                            ApplicationManager.getApplication().invokeLater {
                                val dialog = AlterConstantPropertiesDialog(prefix, alterData)
                                dialog.show()
                            }
                            return PsiElement.EMPTY_ARRAY
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    private fun getAlterDataForPrefix(project: Project, prefix: String): Map<String, String>? {
        val provider = AlterConstantProvider.getInstance(project)
        return provider.getHardcodedDataForPrefix(prefix)
    }
}

