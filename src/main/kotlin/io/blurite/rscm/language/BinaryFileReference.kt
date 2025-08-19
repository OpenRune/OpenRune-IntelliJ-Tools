package io.blurite.rscm.language

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceBase

/**
 * @author Kris | 19/08/2025
 */
class BinaryFileReference(
    source: PsiElement,
    private val targetFile: PsiFile,
    rangeInElement: TextRange
) : PsiReferenceBase<PsiElement>(source, rangeInElement, true) {

    override fun resolve(): PsiElement = targetFile

    override fun handleElementRename(newName: String): PsiElement {
        val vf = targetFile.virtualFile ?: return myElement
        val ext = vf.extension?.let { ".$it" } ?: ""
        WriteCommandAction.runWriteCommandAction(targetFile.project) {
            vf.rename(this, newName + ext)
        }
        return myElement
    }

    override fun isReferenceTo(element: PsiElement): Boolean =
        element == targetFile
}
