package io.blurite.rscm.language.provider

import io.blurite.rscm.language.psi.RSCMProperty

/**
 * Provider interface for RSCM properties.
 * Allows different sources (files, maps, etc.) to provide properties.
 * 
 * @author Auto-generated
 */
interface RSCMProvider {
    /**
     * Get all properties for a given prefix (e.g., "item", "object").
     */
    fun getAllProperties(prefix: String): List<RSCMProperty>
    
    /**
     * Get properties matching a specific key for a given prefix.
     */
    fun getProperties(prefix: String, key: String): List<RSCMProperty>
    
    /**
     * Check if this provider supports the given prefix.
     */
    fun supportsPrefix(prefix: String): Boolean
}



