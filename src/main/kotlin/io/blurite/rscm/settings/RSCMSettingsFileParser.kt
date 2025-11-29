package io.blurite.rscm.settings

import java.io.File
import java.nio.file.Path

/**
 * Parses TOML settings file for RSCM configuration.
 * Settings file format:
 * ```toml
 * [rscm]
 * settings_file = "path/to/settings.toml"  # Optional, for reference
 * 
 * # Directories are relative to the settings file location
 * mappings_directories = [
 *     "mappings",
 *     "../other-mappings"
 * ]
 * 
 * # Provider settings
 * enable_file_provider = true
 * enable_alter_constant_provider = true
 * 
 * # Referential mappings
 * referential_mappings = "component=interface,dbrow=dbtable"
 * ```
 */
object RSCMSettingsFileParser {
    
    data class ParsedSettings(
        val mappingsDirectories: List<String>,
        val enableFileProvider: Boolean,
        val enableAlterConstantProvider: Boolean,
        val referentialMappings: String
    )
    
    /**
     * Parse a TOML settings file and return the settings.
     * Directories are resolved relative to the settings file location.
     */
    fun parseSettingsFile(settingsFilePath: String): ParsedSettings? {
        val file = File(settingsFilePath)
        if (!file.exists() || !file.isFile) {
            return null
        }
        
        val settingsFileDir = file.parentFile?.absolutePath ?: return null
        val lines = file.readLines()
        
        var inRscmSection = false
        val mappingsDirs = mutableListOf<String>()
        var enableFileProvider = true
        var enableAlterConstantProvider = true
        var referentialMappings = ""
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            
            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                i++
                continue
            }
            
            // Check for [rscm] section
            if (line == "[rscm]") {
                inRscmSection = true
                i++
                continue
            }
            
            // If we hit another section, stop parsing
            if (line.startsWith("[") && line.endsWith("]")) {
                if (inRscmSection) break
                i++
                continue
            }
            
            if (inRscmSection) {
                when {
                    line.startsWith("mappings_directories") -> {
                        // Parse array: mappings_directories = ["dir1", "dir2"]
                        val arrayStart = line.indexOf('[')
                        if (arrayStart != -1) {
                            val arrayContent = StringBuilder()
                            arrayContent.append(line.substring(arrayStart + 1))
                            
                            // Continue reading if array spans multiple lines
                            while (!arrayContent.toString().contains("]")) {
                                i++
                                if (i >= lines.size) break
                                arrayContent.append(" ").append(lines[i].trim())
                            }
                            
                            val arrayStr = arrayContent.toString().substringBefore("]")
                            // Extract quoted strings
                            val regex = """["']([^"']+)["']""".toRegex()
                            regex.findAll(arrayStr).forEach { matchResult ->
                                val dir = matchResult.groupValues[1]
                                // Resolve relative to settings file
                                val resolvedPath = if (File(dir).isAbsolute) {
                                    dir
                                } else {
                                    File(settingsFileDir, dir).absolutePath
                                }
                                mappingsDirs.add(resolvedPath)
                            }
                        }
                    }
                    line.startsWith("enable_file_provider") -> {
                        val value = line.substringAfter("=").trim().removeSurrounding("\"", "\"").lowercase()
                        enableFileProvider = value == "true"
                    }
                    line.startsWith("enable_alter_constant_provider") -> {
                        val value = line.substringAfter("=").trim().removeSurrounding("\"", "\"").lowercase()
                        enableAlterConstantProvider = value == "true"
                    }
                    line.startsWith("referential_mappings") -> {
                        val value = line.substringAfter("=").trim().removeSurrounding("\"", "\"")
                        referentialMappings = value
                    }
                }
            }
            
            i++
        }
        
        return ParsedSettings(
            mappingsDirectories = mappingsDirs,
            enableFileProvider = enableFileProvider,
            enableAlterConstantProvider = enableAlterConstantProvider,
            referentialMappings = referentialMappings
        )
    }
}

