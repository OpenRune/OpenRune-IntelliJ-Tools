package dev.openrune.language

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import io.blurite.rscm.language.RSCMFileType
import io.blurite.rscm.language.psi.RSCMFile
import io.blurite.rscm.language.psi.RSCMProperty
import com.intellij.psi.util.PsiTreeUtil

/**
 * Manages temporary virtual files for Alter constant-based RSCM properties.
 * Creates files like "temp_items.rscm" that contain all Alter constant properties for a prefix.
 * 
 * @author Auto-generated
 */
@Service(Service.Level.PROJECT)
class AlterConstantFileManager(private val project: Project) {
    
    private val tempFiles = mutableMapOf<String, RSCMFile>()
    
    /**
     * Get or create a temporary virtual file for the given prefix.
     * The file will contain all Alter constant properties for that prefix.
     */
    fun getOrCreateTempFile(prefix: String, properties: Map<String, String>): RSCMFile? {
        // Check if we already have this file cached (thread-safe)
        synchronized(tempFiles) {
            val existingFile = tempFiles[prefix]
            if (existingFile != null && existingFile.isValid) {
                return existingFile
            }
        }
        
        // Create the file content from all properties
        val fileContent = properties.entries
            .joinToString("\n") { (key, value) -> "$key=$value" }
        
        // Create PSI file from text - PsiFileFactory will create a LightVirtualFile automatically
        val fileName = "temp_$prefix.${RSCMFileType.INSTANCE.defaultExtension}"
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText(fileName, RSCMFileType.INSTANCE, fileContent) as? RSCMFile
        
        if (psiFile != null) {
            // Cache it (thread-safe)
            synchronized(tempFiles) {
                tempFiles[prefix] = psiFile
            }
        }
        
        return psiFile
    }
    
    /**
     * Get all properties from the temporary file for a prefix.
     */
    fun getAllProperties(prefix: String, properties: Map<String, String>): List<RSCMProperty> {
        val rscmFile = getOrCreateTempFile(prefix, properties) ?: return emptyList()
        val result = mutableListOf<RSCMProperty>()
        val psiProperties = PsiTreeUtil.getChildrenOfType(rscmFile, RSCMProperty::class.java)
        if (psiProperties != null) {
            result.addAll(psiProperties)
        }
        return result
    }
    
    /**
     * Get a specific property from the temporary file for a prefix.
     */
    fun getProperty(prefix: String, key: String, properties: Map<String, String>): List<RSCMProperty> {
        val rscmFile = getOrCreateTempFile(prefix, properties) ?: return emptyList()
        val result = mutableListOf<RSCMProperty>()
        val psiProperties = PsiTreeUtil.getChildrenOfType(rscmFile, RSCMProperty::class.java)
        if (psiProperties != null) {
            for (property in psiProperties) {
                if (key == property.key) {
                    result.add(property)
                }
            }
        }
        return result
    }
    
    /**
     * Clear the cache of temp files (called when source files change).
     */
    fun clearCache() {
        synchronized(tempFiles) {
            tempFiles.clear()
        }
    }
    
    companion object {
        fun getInstance(project: Project): AlterConstantFileManager {
            return project.getService(AlterConstantFileManager::class.java)
        }
    }
}

