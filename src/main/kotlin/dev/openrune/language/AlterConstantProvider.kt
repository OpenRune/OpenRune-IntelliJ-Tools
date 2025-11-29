package dev.openrune.language

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import io.blurite.rscm.language.provider.RSCMProvider
import io.blurite.rscm.language.psi.RSCMProperty
import io.blurite.rscm.settings.RSCMProjectSettings
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Provider that loads properties from .dat and .toml files for Alter constants.
 * - Decodes binary .dat files and provides properties from them.
 * - Parses gamevals.toml files with sections like [gamevals.prefix] where prefix becomes the table name.
 * Uses temporary virtual files so properties have proper virtual files for documentation/navigation.
 * 
 * @author Auto-generated
 */
@Service(Service.Level.PROJECT)
class AlterConstantProvider(private val project: Project) : RSCMProvider {
    
    // Cached data loaded from .dat and .toml files - can be reloaded on file changes
    // Format: prefix (table name) -> (key -> value)
    private val datDataRef = AtomicReference<Map<String, Map<String, String>>>(loadAllFiles())
    
    // Track which keys come from TOML files (for navigation)
    // Format: "prefix:key" -> TOML file path
    private val tomlSourceFilesRef = AtomicReference<Map<String, String>>(loadTomlSourceFiles())
    
    // Cache PSI properties per prefix to avoid recreating them
    // Format: prefix -> List<RSCMProperty>
    private val cachedProperties = mutableMapOf<String, List<RSCMProperty>>()
    
    // Track mappings directories for file watching
    private val mappingsDirectories: List<String>
        get() = RSCMProjectSettings.getInstance(project).getEffectiveSettings().mappingsPaths.filter { it.isNotEmpty() }
    
    private val fileManager: AlterConstantFileManager
        get() = AlterConstantFileManager.getInstance(project)
    
    // Helper to get current data (thread-safe)
    private val datData: Map<String, Map<String, String>>
        get() = datDataRef.get()
    
    private val tomlSourceFiles: Map<String, String>
        get() = tomlSourceFilesRef.get()
    
    init {
        // Pre-cache properties for all prefixes to avoid lazy creation during typing/autocomplete
        // This happens once on service initialization, not during typing
        for (prefix in datData.keys) {
            getCachedProperties(prefix)
        }
        
        // Set up file watcher to reload on changes
        setupFileWatcher()
    }
    
    /**
     * Set up file watcher to reload mappings when .dat or .toml files change.
     */
    private fun setupFileWatcher() {
        val messageBus = project.messageBus.connect()
        messageBus.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val directories = mappingsDirectories
                if (directories.isEmpty()) return
                
                var shouldReload = false
                val normalizedDirs = directories.map { File(it).absolutePath.normalizePath() }
                
                for (event in events) {
                    val file = event.file ?: continue
                    val filePath = file.path.normalizePath()
                    
                    // Check if the file is in any of our mappings directories
                    for (dirPath in normalizedDirs) {
                        if (filePath.startsWith(dirPath)) {
                            // Check if it's a .dat or .toml file
                            if (filePath.endsWith(".dat") || filePath.endsWith(".toml")) {
                                shouldReload = true
                                break
                            }
                        }
                    }
                    if (shouldReload) break
                }
                
                if (shouldReload) {
                    // Reload on background thread to avoid blocking UI
                    AppExecutorUtil.getAppExecutorService().execute {
                        reloadMappings()
                    }
                }
            }
        })
    }
    
    /**
     * Normalize path separators for comparison (handles Windows/Unix differences).
     */
    private fun String.normalizePath(): String {
        return this.replace("\\", "/").lowercase()
    }
    
    /**
     * Reload all mappings from files and clear cache.
     * Can be called from any thread - will handle threading internally.
     */
    fun reloadMappings() {
        // Run on background thread to avoid blocking UI
        AppExecutorUtil.getAppExecutorService().execute {
            // Load new data (file I/O, safe on background thread)
            val newData = loadAllFiles()
            val newTomlSources = loadTomlSourceFiles()
            
            // Update references atomically
            datDataRef.set(newData)
            tomlSourceFilesRef.set(newTomlSources)
            
            // Clear cached PSI properties - they'll be recreated on next access
            synchronized(cachedProperties) {
                cachedProperties.clear()
            }
            
            // Clear file manager's temp file cache as well
            fileManager.clearCache()
            
            // Pre-cache properties for all prefixes again (PSI operations need read action)
            ReadAction.run<RuntimeException> {
                for (prefix in newData.keys) {
                    getCachedProperties(prefix)
                }
            }
        }
    }
    
    /**
     * Load all .dat and .toml files from all mappings directories.
     */
    private fun loadAllFiles(): Map<String, Map<String, String>> {
        val mappings = mutableMapOf<String, MutableMap<String, String>>()
        val directories = mappingsDirectories
        
        if (directories.isEmpty()) {
            return emptyMap()
        }
        
        // Load from all directories
        for (mappingDirectory in directories) {
            val dir = Path.of(mappingDirectory).toFile()
            if (!dir.exists() || !dir.isDirectory) {
                continue
            }
            
            // Load .dat files
            val datFiles = dir.listFiles { file -> file.isFile && file.extension == "dat" }
            datFiles?.forEach { datFile ->
                try {
                    decodeGameValDat(datFile, mappings)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Load .toml files (find all gamevals.toml files recursively)
            findGameValsTomlFiles(dir).forEach { tomlFile ->
                try {
                    parseGameValsToml(tomlFile, mappings)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return mappings
    }
    
    /**
     * Track which keys come from TOML files for navigation purposes.
     * Tracks at the key level, not prefix level, so we can navigate to the exact file containing each key.
     */
    private fun loadTomlSourceFiles(): Map<String, String> {
        val tomlSources = mutableMapOf<String, String>()
        val directories = mappingsDirectories
        
        if (directories.isEmpty()) {
            return emptyMap()
        }
        
        // Search all directories
        for (mappingDirectory in directories) {
            val dir = Path.of(mappingDirectory).toFile()
            if (!dir.exists() || !dir.isDirectory) {
                continue
            }
            
            // Find all gamevals.toml files recursively and track which keys they contain
            findGameValsTomlFiles(dir).forEach { tomlFile ->
                try {
                    val lines = tomlFile.readLines()
                    var currentPrefix: String? = null
                    
                    for (line in lines) {
                        val trimmed = line.trim()
                        
                        // Skip empty lines and comments
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue
                        }
                        
                        // Check for section header: [gamevals.prefix]
                        val sectionMatch = Regex("""^\[gamevals\.([^\]]+)\]$""").find(trimmed)
                        if (sectionMatch != null) {
                            currentPrefix = sectionMatch.groupValues[1]
                            continue
                        }
                        
                        // Parse key-value pairs and track which file each key comes from
                        if (currentPrefix != null && trimmed.contains("=")) {
                            val parts = trimmed.split("=", limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                // Track "prefix:key" -> TOML file path
                                val keyPath = "$currentPrefix:$key"
                                tomlSources[keyPath] = tomlFile.absolutePath
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return tomlSources
    }
    
    /**
     * Decode a .dat file and populate the mappings map.
     */
    private fun decodeGameValDat(datFile: File, mappings: MutableMap<String, MutableMap<String, String>>) {
        DataInputStream(FileInputStream(datFile)).use { input ->
            val tableCount = input.readInt()
            
            repeat(tableCount) {
                val nameLength = input.readShort().toInt()
                val nameBytes = ByteArray(nameLength)
                input.readFully(nameBytes)
                val tableName = String(nameBytes, Charsets.UTF_8)
                
                val itemCount = input.readInt()
                mappings.putIfAbsent(tableName, mutableMapOf())
                val tableMap = mappings[tableName]!!
                
                repeat(itemCount) {
                    val itemLength = input.readShort().toInt()
                    val itemBytes = ByteArray(itemLength)
                    input.readFully(itemBytes)
                    val itemString = String(itemBytes, Charsets.UTF_8).split("=")
                    
                    if (itemString.size >= 2) {
                        val key = itemString[0]
                        val value = itemString[1]
                        tableMap[key] = value
                    }
                }
            }
        }
    }
    
    /**
     * Find all gamevals.toml files recursively in a directory.
     */
    private fun findGameValsTomlFiles(directory: File): List<File> {
        val gameValsFiles = mutableListOf<File>()
        
        fun searchDir(dir: File) {
            if (!dir.exists() || !dir.isDirectory) {
                return
            }
            
            // Check files in current directory
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name == "gamevals.toml") {
                    gameValsFiles.add(file)
                } else if (file.isDirectory) {
                    // Recursively search subdirectories
                    searchDir(file)
                }
            }
        }
        
        searchDir(directory)
        return gameValsFiles
    }
    
    /**
     * Parse a gamevals.toml file and populate the mappings map.
     * Format: [gamevals.prefix] sections where prefix becomes the table name.
     * Example: [gamevals.dbrows] -> prefix is "dbrows"
     * If the same prefix appears in multiple files, keys are merged (last value wins for duplicates).
     */
    private fun parseGameValsToml(tomlFile: File, mappings: MutableMap<String, MutableMap<String, String>>) {
        val lines = tomlFile.readLines()
        var currentPrefix: String? = null
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }
            
            // Check for section header: [gamevals.prefix]
            val sectionMatch = Regex("""^\[gamevals\.([^\]]+)\]$""").find(trimmed)
            if (sectionMatch != null) {
                currentPrefix = sectionMatch.groupValues[1]
                mappings.putIfAbsent(currentPrefix, mutableMapOf())
                continue
            }
            
            // Parse key-value pairs: key=value
            if (currentPrefix != null && trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    mappings[currentPrefix]?.put(key, value)
                }
            }
        }
    }
    
    /**
     * Get or create cached PSI properties for a prefix.
     * This caches the PSI properties to avoid expensive PSI file creation/parsing.
     * Thread-safe: uses synchronized block for cache access.
     */
    private fun getCachedProperties(prefix: String): List<RSCMProperty> {
        // Check cache first (with synchronization)
        synchronized(cachedProperties) {
            cachedProperties[prefix]?.let { return it }
        }
        
        // Not in cache, create it
        val prefixData = datData[prefix] ?: return emptyList()
        val properties = fileManager.getAllProperties(prefix, prefixData)
        
        // Store in cache (with synchronization)
        synchronized(cachedProperties) {
            cachedProperties[prefix] = properties
        }
        
        return properties
    }
    
    override fun getAllProperties(prefix: String): List<RSCMProperty> {
        if (!datData.containsKey(prefix)) return emptyList()
        // Return cached properties - no PSI creation on every call
        return getCachedProperties(prefix)
    }
    
    override fun getProperties(prefix: String, key: String): List<RSCMProperty> {
        if (!datData.containsKey(prefix)) return emptyList()
        // Filter from cached properties - very fast, no PSI operations
        return getCachedProperties(prefix).filter { it.key == key }
    }
    
    override fun supportsPrefix(prefix: String): Boolean {
        return datData.containsKey(prefix)
    }
    
    /**
     * Get data for a prefix (used for navigation dialog).
     */
    fun getHardcodedDataForPrefix(prefix: String): Map<String, String>? {
        return datData[prefix]
    }
    
    /**
     * Get the TOML source file path for a specific key in a prefix, if it comes from a TOML file.
     * Returns null if the key comes from a .dat file.
     */
    fun getTomlSourceFile(prefix: String, key: String? = null): String? {
        if (key != null) {
            // Look up specific key: "prefix:key"
            val keyPath = "$prefix:$key"
            return tomlSourceFiles[keyPath]
        }
        // Fallback: check if any key from this prefix comes from a TOML file
        return tomlSourceFiles.entries.firstOrNull { it.key.startsWith("$prefix:") }?.value
    }
    
    companion object {
        fun getInstance(project: Project): AlterConstantProvider {
            return project.getService(AlterConstantProvider::class.java)
        }
    }
}

