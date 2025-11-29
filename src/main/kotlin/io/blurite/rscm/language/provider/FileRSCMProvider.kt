package io.blurite.rscm.language.provider

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import io.blurite.rscm.language.RSCMFileType
import io.blurite.rscm.language.RSCMUtil
import io.blurite.rscm.language.psi.RSCMFile
import io.blurite.rscm.language.psi.RSCMProperty

/**
 * Provider that loads properties from .rscm files.
 * This wraps the existing file-based loading mechanism.
 * 
 * @author Auto-generated
 */
class FileRSCMProvider(private val project: Project) : RSCMProvider {
    
    override fun getAllProperties(prefix: String): List<RSCMProperty> {
        if (!supportsPrefix(prefix)) {
            return emptyList()
        }
        
        val path = RSCMUtil.constructPath(project, prefix) ?: return emptyList()
        val vf = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) ?: return emptyList()
        val rscmFile = PsiManager.getInstance(project).findFile(vf) as? RSCMFile ?: return emptyList()
        
        val result = mutableListOf<RSCMProperty>()
        val properties = PsiTreeUtil.getChildrenOfType(rscmFile, RSCMProperty::class.java)
        if (properties != null) {
            result.addAll(properties)
        }
        return result
    }
    
    override fun getProperties(prefix: String, key: String): List<RSCMProperty> {
        if (!supportsPrefix(prefix)) {
            return emptyList()
        }
        
        val path = RSCMUtil.constructPath(project, prefix) ?: return emptyList()
        val vf = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) ?: return emptyList()
        val rscmFile = PsiManager.getInstance(project).findFile(vf) as? RSCMFile ?: return emptyList()
        
        val result = mutableListOf<RSCMProperty>()
        val properties = PsiTreeUtil.getChildrenOfType(rscmFile, RSCMProperty::class.java)
        if (properties != null) {
            for (property in properties) {
                if (key == property.key) {
                    result.add(property)
                }
            }
        }
        return result
    }
    
    override fun supportsPrefix(prefix: String): Boolean {
        // Check file system directly to avoid circular dependency with RSCMUtil.isValidPrefix()
        return RSCMUtil.isValidPrefixFromFileSystem(project, prefix)
    }
}

