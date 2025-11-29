package io.blurite.rscm.language.provider

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.openrune.language.AlterConstantProvider
import io.blurite.rscm.language.psi.RSCMProperty

/**
 * Manages multiple RSCM providers and combines their results.
 * Respects enable/disable settings for each provider.
 * 
 * @author Auto-generated
 */
@Service(Service.Level.PROJECT)
class RSCMProviderManager(private val project: Project) {
    
    private val fileProvider = FileRSCMProvider(project)
    private val alterConstantProvider = AlterConstantProvider.getInstance(project)
    
    private val settings: io.blurite.rscm.settings.RSCMProjectSettings
        get() = io.blurite.rscm.settings.RSCMProjectSettings.getInstance(project)
    
    /**
     * Get all enabled providers.
     */
    private fun getEnabledProviders(): List<RSCMProvider> {
        val enabledProviders = mutableListOf<RSCMProvider>()
        val effectiveSettings = settings.getEffectiveSettings()
        
        if (effectiveSettings.enableFileProvider) {
            enabledProviders.add(fileProvider)
        }
        
        if (effectiveSettings.enableAlterConstantProvider) {
            enabledProviders.add(alterConstantProvider)
        }
        
        return enabledProviders
    }
    
    /**
     * Get all properties from all enabled providers that support the given prefix.
     */
    fun getAllProperties(prefix: String): List<RSCMProperty> {
        val result = mutableListOf<RSCMProperty>()
        
        for (provider in getEnabledProviders()) {
            if (provider.supportsPrefix(prefix)) {
                result.addAll(provider.getAllProperties(prefix))
            }
        }
        
        return result
    }
    
    /**
     * Get properties matching a specific key from all enabled providers that support the given prefix.
     */
    fun getProperties(prefix: String, key: String): List<RSCMProperty> {
        val result = mutableListOf<RSCMProperty>()
        
        for (provider in getEnabledProviders()) {
            if (provider.supportsPrefix(prefix)) {
                result.addAll(provider.getProperties(prefix, key))
            }
        }
        
        return result
    }
    
    /**
     * Check if any enabled provider supports the given prefix.
     */
    fun supportsPrefix(prefix: String): Boolean {
        return getEnabledProviders().any { it.supportsPrefix(prefix) }
    }
    
    companion object {
        fun getInstance(project: Project): RSCMProviderManager {
            return project.getService(RSCMProviderManager::class.java)
        }
    }
}

