package dev.openrune.language

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import io.blurite.rscm.language.psi.RSCMProperty

/**
 * Custom ResolveResult for Alter constant-based properties.
 * - If from TOML file: navigates to the TOML file
 * - If from .dat file: shows a dialog
 * 
 * @author Auto-generated
 */
class AlterConstantResolveResult(
    element: PsiElement,
    private val prefix: String,
    private val allProperties: Map<String, String>,
    private val tomlSourceFile: String? = null
) : PsiElementResolveResult(element) {
    
    override fun getElement(): PsiElement {
        val originalElement = super.getElement()
        if (originalElement is RSCMProperty) {
            // Return a wrapper that intercepts navigation
            return AlterConstantNavigatablePropertyWrapper(originalElement, prefix, allProperties, tomlSourceFile)
        }
        return originalElement
    }
}

/**
 * Wrapper for RSCMProperty that intercepts navigation.
 * - If from TOML: navigates to the TOML file
 * - If from .dat: shows a dialog
 */
private class AlterConstantNavigatablePropertyWrapper(
    private val property: RSCMProperty,
    private val prefix: String,
    private val allProperties: Map<String, String>,
    private val tomlSourceFile: String?
) : ASTWrapperPsiElement(property.node),
    RSCMProperty, 
    Navigatable {
    
    // Delegate all RSCMProperty methods to the original property
    override fun getKey(): String? = property.key
    override fun getValue(): String? = property.value
    override fun getName(): String? = property.name
    override fun setName(newName: String): PsiElement = property.setName(newName)
    override fun getNameIdentifier(): PsiElement? = property.nameIdentifier
    
    // Implement Navigatable
    override fun navigate(requestFocus: Boolean) {
        // If from TOML file, navigate to the actual TOML file
        if (tomlSourceFile != null) {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(tomlSourceFile)
            if (virtualFile != null) {
                val psiFile = PsiManager.getInstance(property.project).findFile(virtualFile)
                if (psiFile != null) {
                    // Find the line with the key within the correct prefix section
                    val key = property.key
                    if (key != null) {
                        // Split by line breaks, handling both \n and \r\n
                        val text = psiFile.text
                        val lines = text.split(Regex("\r?\n"))
                        var currentPrefix: String? = null
                        var inCorrectSection = false
                        
                        for ((index, line) in lines.withIndex()) {
                            val trimmed = line.trim()
                            
                            // Skip empty lines and comments
                            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                                continue
                            }
                            
                            // Check for section header: [gamevals.prefix]
                            val sectionMatch = Regex("""^\[gamevals\.([^\]]+)\]$""").find(trimmed)
                            if (sectionMatch != null) {
                                currentPrefix = sectionMatch.groupValues[1]
                                inCorrectSection = (currentPrefix == prefix)
                                continue
                            }
                            
                            // Only search for the key within the correct prefix section
                            if (inCorrectSection) {
                                // Match key=value pattern (key may have leading/trailing whitespace)
                                val keyValuePattern = Regex("""^\s*${Regex.escape(key)}\s*=\s*.*$""")
                                if (keyValuePattern.matches(trimmed)) {
                                    // Find the column where the key starts (after any leading whitespace)
                                    val keyStartColumn = line.indexOf(key)
                                    if (keyStartColumn >= 0) {
                                        // Navigate to this line and column (0-indexed)
                                        OpenFileDescriptor(
                                            property.project,
                                            virtualFile,
                                            index,
                                            keyStartColumn
                                        ).navigate(requestFocus)
                                        return
                                    } else {
                                        // Fallback: navigate to line start if key not found in original line
                                        OpenFileDescriptor(
                                            property.project,
                                            virtualFile,
                                            index,
                                            0
                                        ).navigate(requestFocus)
                                        return
                                    }
                                }
                            }
                        }
                    }
                    // Fallback: just open the file at the beginning
                    OpenFileDescriptor(
                        property.project,
                        virtualFile
                    ).navigate(requestFocus)
                    return
                }
            }
        }
        
        // If from .dat file or TOML navigation failed, show dialog
        val dialog = AlterConstantPropertiesDialog(prefix, allProperties)
        dialog.show()
    }
    
    override fun canNavigate(): Boolean = true
    
    override fun canNavigateToSource(): Boolean = true
}

