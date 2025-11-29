package io.blurite.rscm.language.marker

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import dev.openrune.language.AlterConstantProvider
import io.blurite.rscm.language.RSCMIcons
import io.blurite.rscm.language.RSCMUtil
import io.blurite.rscm.language.annotator.RSCMAnnotator

/**
 * @author Chris
 * @since 12/5/2020
 */
abstract class RSCMLineMarkerProvider : RelatedItemLineMarkerProvider() {
    fun collectNavigationMarkers(
        value: String,
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>?>,
    ) {
        if (!value.contains(RSCMAnnotator.RSCM_SEPARATOR_STR)) return
        val prefix = value.substringAfter("\"").substringBefore(RSCMAnnotator.RSCM_SEPARATOR_STR)
        val project = element.project
        if (!RSCMUtil.isValidPrefix(project, prefix)) return
        
        // Get or create RSCM file (handles both file-based and map-only prefixes)
        val rscmFile = RSCMUtil.getOrCreateRSCMFile(project, prefix) ?: return
        
        val possibleProperties = value.substring(prefix.length + RSCMAnnotator.RSCM_SEPARATOR_STR.length)
        
        // Get properties from providers (includes both file and map)
        val properties = RSCMUtil.findPropertiesFromProviders(rscmFile, possibleProperties)
        
        if (properties.isEmpty()) return
        
        // Check if any properties are from Alter constant provider (temp_ files)
        val hasAlterConstantProperties = properties.any { prop ->
            val fileName = prop.containingFile.name
            fileName.startsWith("temp_") && fileName.endsWith(".rscm")
        }
        
        // Determine icon and tooltip based on source
        val icon: javax.swing.Icon
        val tooltip: String
        
        if (hasAlterConstantProperties) {
            // Check if any Alter constant properties are from TOML files
            val alterProvider = AlterConstantProvider.getInstance(project)
            val hasTomlProperties = properties.any { prop ->
                val fileName = prop.containingFile.name
                if (fileName.startsWith("temp_") && fileName.endsWith(".rscm")) {
                    val propPrefix = fileName.removePrefix("temp_").removeSuffix(".rscm")
                    val propKey = prop.key
                    alterProvider.getTomlSourceFile(propPrefix, propKey) != null
                } else {
                    false
                }
            }
            
            if (hasTomlProperties) {
                // TOML file - use file icon, navigate to file
                icon = RSCMIcons.FILE
                tooltip = "Navigate to TOML file"
            } else {
                // .dat file - use view icon, show dialog
                icon = RSCMIcons.MAP
                tooltip = "View Alter constant provider properties (not a file)"
            }
        } else {
            // Regular .rscm file
            icon = RSCMIcons.FILE
            tooltip = "Navigate to RSCM language property"
        }
        
        // Add the property to a collection of line marker info
        val builder =
            NavigationGutterIconBuilder
                .create(icon)
                .setTargets(properties)
                .setTooltipText(tooltip)
        result.add(builder.createLineMarkerInfo(element))
    }
}
