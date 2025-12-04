package io.blurite.rscm.language

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import java.nio.file.Path

/**
 * Reference for direct file references (e.g., "items.dat", "gamevals.toml", "items.rscm").
 * Allows Ctrl+Click navigation to the actual file.
 */
class RSCMFileReference(
    private val filePath: Path,
    element: PsiElement,
    textRange: TextRange,
) : PsiReferenceBase<PsiElement>(element, textRange) {
    
    override fun resolve(): PsiElement? {
        val vf = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath) ?: return null
        return PsiManager.getInstance(element.project).findFile(vf)
    }
    
    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element !is PsiFile) return false
        val vf = element.virtualFile ?: return false
        return vf.path == filePath.toString() || vf.toNioPath() == filePath
    }
    
    override fun getCanonicalText(): String {
        return filePath.fileName.toString()
    }
}



