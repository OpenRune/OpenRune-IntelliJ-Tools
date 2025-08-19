package io.blurite.rscm.language.completion

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry

/**
 * @author Chris | 5/3/21
 */
class RSCMKotlinCompletionConfidence : RSCMCompletionConfidence() {
    override fun shouldSkipAutopopup(
        editor: Editor,
        element: PsiElement,
        psiFile: PsiFile,
        offset: Int
    ): ThreeState {
        if (element.parent !is KtLiteralStringTemplateEntry) return ThreeState.UNSURE
        val value = element.parent.text
        return shouldSkipAutopopup(value, element.parent)
    }
}
