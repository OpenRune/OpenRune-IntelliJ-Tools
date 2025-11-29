package io.blurite.rscm.language

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.util.PsiTreeUtil
import dev.openrune.language.AlterConstantFileManager
import dev.openrune.language.AlterConstantProvider
import io.blurite.rscm.language.provider.RSCMProviderManager
import io.blurite.rscm.language.psi.RSCMFile
import io.blurite.rscm.language.psi.RSCMProperty
import io.blurite.rscm.language.util.toMap
import io.blurite.rscm.settings.RSCMProjectSettings
import java.nio.file.Path
import java.util.*

/**
 * @author Chris
 * @since 12/3/2020
 */
object RSCMUtil {
    /**
     * Searches the specified RSCM file for the RSCM property with the given key.
     */
    @JvmStatic
    fun findProperties(
        file: RSCMFile,
        key: String,
    ): List<RSCMProperty> {
        val result = mutableListOf<RSCMProperty>()
        val properties = PsiTreeUtil.getChildrenOfType(file, RSCMProperty::class.java)
        if (properties != null) {
            for (property in properties) {
                if (key == property.key) {
                    result.add(property)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun findProperties(file: RSCMFile): List<RSCMProperty> {
        val result = mutableListOf<RSCMProperty>()
        val properties = PsiTreeUtil.getChildrenOfType(file, RSCMProperty::class.java)
        Collections.addAll(result, *properties)
        return result
    }

    /**
     * Check if a prefix is valid by checking the file system directly.
     * This is used by FileRSCMProvider to avoid circular dependencies.
     */
    fun isValidPrefixFromFileSystem(
        project: Project,
        prefix: String,
    ): Boolean {
        val settings = RSCMProjectSettings.getInstance(project)
        val effectiveSettings = settings.getEffectiveSettings()
        val mappingDirectories = effectiveSettings.mappingsPaths.filter { it.isNotEmpty() }
        
        // Check all directories
        for (mappingDirectory in mappingDirectories) {
            val dir = Path.of(mappingDirectory).toFile()
            if (dir.exists() && dir.isDirectory) {
                val found = dir.listFiles()
                    ?.map { it.nameWithoutExtension }
                    ?.any { it == prefix } ?: false
                if (found) return true
            }
        }
        return false
    }
    
    fun isValidPrefix(
        project: Project,
        prefix: String,
    ): Boolean {
        // Check provider manager first (includes both file and Alter constant providers)
        val providerManager = RSCMProviderManager.getInstance(project)
        if (providerManager.supportsPrefix(prefix)) {
            return true
        }
        
        // Fallback to file-based check for backward compatibility
        return isValidPrefixFromFileSystem(project, prefix)
    }
    
    /**
     * Extract prefix from RSCM file name (e.g., "items.rscm" -> "items", "temp_objects.rscm" -> "objects").
     */
    private fun getPrefixFromFile(file: RSCMFile): String? {
        val fileName = file.name
        val vFile = file.virtualFile
        var nameWithoutExtension = vFile?.nameWithoutExtension ?: FileUtilRt.getNameWithoutExtension(fileName)
        
        // Handle temp files from Alter constant provider (temp_prefix.rscm -> prefix)
        if (nameWithoutExtension.startsWith("temp_")) {
            nameWithoutExtension = nameWithoutExtension.removePrefix("temp_")
        }
        
        return nameWithoutExtension
    }
    
    /**
     * Get or create an RSCM file for a prefix.
     * Tries to get the actual file first, then falls back to Alter constant provider temp file if needed.
     */
    fun getOrCreateRSCMFile(project: Project, prefix: String): RSCMFile? {
        // Try to get the actual file first
        val path = constructPath(project, prefix)
        val rscmFile = if (path != null) {
            val vf = com.intellij.openapi.vfs.VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
            if (vf != null) {
                com.intellij.psi.PsiManager.getInstance(project).findFile(vf) as? RSCMFile
            } else {
                null
            }
        } else {
            null
        }
        
        // If file doesn't exist, check if it's an Alter constant-only prefix and create temp file
        if (rscmFile == null) {
            val alterProvider = AlterConstantProvider.getInstance(project)
            val alterData = alterProvider.getHardcodedDataForPrefix(prefix)
            
            if (alterData != null) {
                // Use temp file from Alter constant provider
                val fileManager = AlterConstantFileManager.getInstance(project)
                return fileManager.getOrCreateTempFile(prefix, alterData)
            }
        }
        
        return rscmFile
    }
    
    /**
     * Find properties using the provider system (combines file and Alter constant providers).
     * This is the new method that queries all providers.
     */
    fun findPropertiesFromProviders(
        file: RSCMFile,
        key: String? = null,
    ): List<RSCMProperty> {
        val project = file.project
        val prefix = getPrefixFromFile(file) ?: return emptyList()
        val providerManager = RSCMProviderManager.getInstance(project)
        
        return if (key != null) {
            providerManager.getProperties(prefix, key)
        } else {
            providerManager.getAllProperties(prefix)
        }
    }

    fun constructPath(
        project: Project,
        prefix: String,
    ): Path? {
        val settings = RSCMProjectSettings.getInstance(project)
        val effectiveSettings = settings.getEffectiveSettings()
        val mappingDirectories = effectiveSettings.mappingsPaths.filter { it.isNotEmpty() }
        
        // Search all directories for the file
        for (mappingDirectory in mappingDirectories) {
            val path = Path.of(mappingDirectory, "$prefix.${RSCMFileType.INSTANCE.defaultExtension}")
            val file = path.toFile()
            if (file.exists() && file.isFile) {
                return path
            }
        }
        
        // If not found, return path from first directory (for backward compatibility)
        return if (mappingDirectories.isNotEmpty()) {
            Path.of(mappingDirectories[0], "$prefix.${RSCMFileType.INSTANCE.defaultExtension}")
        } else {
            null
        }
    }
    
    /**
     * Find a file by name in all mappings directories.
     * Supports .rscm, .dat, and .toml files.
     */
    fun findFileInMappingsDirectories(
        project: Project,
        fileName: String,
    ): java.nio.file.Path? {
        val settings = RSCMProjectSettings.getInstance(project)
        val effectiveSettings = settings.getEffectiveSettings()
        val mappingDirectories = effectiveSettings.mappingsPaths.filter { it.isNotEmpty() }
        
        // Validate fileName - reject if it looks like an error message or contains invalid path characters
        if (fileName.isEmpty() || 
            fileName.length > 255 || // Max filename length on most systems
            fileName.contains("\n") || 
            fileName.contains("\r") ||
            fileName.contains(":") || // Colons are invalid in filenames (except drive letters, but we don't allow paths)
            fileName.contains("Expected") || // Likely an error message
            fileName.contains("error") || // Likely an error message
            fileName.contains("Error") || // Likely an error message
            fileName.contains("Exception")) { // Likely an error message
            return null
        }
        
        // Check if fileName has a valid extension
        if (!fileName.endsWith(".rscm") && !fileName.endsWith(".dat") && !fileName.endsWith(".toml")) {
            return null
        }
        
        // Additional validation: ensure fileName doesn't contain path separators (should be just a filename)
        if (fileName.contains("/") || fileName.contains("\\")) {
            return null
        }
        
        // Search all directories for the file
        for (mappingDirectory in mappingDirectories) {
            try {
                val path = Path.of(mappingDirectory, fileName)
                val file = path.toFile()
                if (file.exists() && file.isFile) {
                    return path
                }
            } catch (e: java.nio.file.InvalidPathException) {
                // Skip invalid paths and continue searching
                continue
            }
        }
        
        return null
    }

    fun isReferentialMapping(
        project: Project,
        prefix: String,
    ): Boolean {
        val settings = RSCMProjectSettings.getInstance(project)
        return prefix in settings.referentialMappings.toMap()
    }

    fun getMappingReference(
        project: Project,
        prefix: String,
    ): String {
        val settings = RSCMProjectSettings.getInstance(project)
        return settings.referentialMappings.toMap()[prefix]!!
    }
}
