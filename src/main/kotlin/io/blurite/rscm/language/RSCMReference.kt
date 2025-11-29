package io.blurite.rscm.language

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import dev.openrune.language.AlterConstantProvider
import dev.openrune.language.AlterConstantResolveResult
import io.blurite.rscm.language.psi.RSCMFile
import io.blurite.rscm.language.psi.RSCMProperty

/**
 * @author Chris
 * @since 12/4/2020
 */
class RSCMReference(
    val file: RSCMFile,
    element: PsiElement,
    textRange: TextRange,
) : PsiReferenceBase<PsiElement>(element, textRange),
    PsiPolyVariantReference {
    private val key = getKey()

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        // Use provider system to get properties from all sources
        val properties = RSCMUtil.findPropertiesFromProviders(file, key)
        val results = mutableListOf<ResolveResult>()
        val project = element.project
        
        for (property in properties) {
            // Check if this is an Alter constant-based property (from temp file)
            val containingFile = property.containingFile
            val fileName = containingFile.name
            
            if (fileName.startsWith("temp_") && fileName.endsWith(".rscm")) {
                // This is an Alter constant property - check if it's from TOML or .dat
                val prefix = fileName.removePrefix("temp_").removeSuffix(".rscm")
                val alterProvider = AlterConstantProvider.getInstance(project)
                val alterData = alterProvider.getHardcodedDataForPrefix(prefix)
                
                if (alterData != null) {
                    // Check if this specific key comes from a TOML file
                    val propertyKey = property.key
                    val tomlSourceFile = alterProvider.getTomlSourceFile(prefix, propertyKey)
                    results.add(AlterConstantResolveResult(property, prefix, alterData, tomlSourceFile))
                    continue
                }
            }
            
            // Regular file-based property
            results.add(PsiElementResolveResult(property))
        }
        return results.toTypedArray()
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults.first().element else null
    }

    override fun getVariants(): Array<Any?> {
        // Use provider system to get properties from all sources
        val properties: List<RSCMProperty> = RSCMUtil.findPropertiesFromProviders(file)
        val variants: MutableList<LookupElement> = ArrayList()
        val project = element.project
        val alterProvider = AlterConstantProvider.getInstance(project)
        
        for (property in properties) {
            if (property.key != null && property.key!!.isNotEmpty()) {
                val fileName = property.containingFile?.name ?: "provider"
                // Determine icon based on source
                val icon: javax.swing.Icon
                val isAlterConstantProperty = fileName.startsWith("temp_") && fileName.endsWith(".rscm")
                
                if (isAlterConstantProperty) {
                    // Check if it's from TOML or .dat (check specific key if available)
                    val prefix = fileName.removePrefix("temp_").removeSuffix(".rscm")
                    val propertyKey = property.key
                    val isFromToml = alterProvider.getTomlSourceFile(prefix, propertyKey) != null
                    icon = if (isFromToml) RSCMIcons.FILE else RSCMIcons.MAP
                } else {
                    // Regular .rscm file
                    icon = RSCMIcons.FILE
                }
                
                variants.add(
                    LookupElementBuilder
                        .create(property)
                        .withIcon(icon)
                        .withTailText(" ${property.value}")
                        .withTypeText(fileName),
                )
            }
        }
        return variants.toTypedArray()
    }

    private fun getKey() =
        if (element is PsiFile) {
            (element as PsiFile).name.substring(rangeInElement.startOffset, rangeInElement.endOffset)
        } else {
            element.text.substring(rangeInElement.startOffset, rangeInElement.endOffset)
        }
}
