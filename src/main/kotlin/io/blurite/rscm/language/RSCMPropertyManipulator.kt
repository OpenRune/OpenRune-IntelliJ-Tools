package io.blurite.rscm.language

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import io.blurite.rscm.language.psi.RSCMElementFactory
import io.blurite.rscm.language.psi.RSCMProperty

class RSCMPropertyManipulator : AbstractElementManipulator<RSCMProperty>() {
    override fun handleContentChange(
        element: RSCMProperty,
        range: TextRange,
        newContent: String,
    ): RSCMProperty {
        val oldText = element.text
        val newText = oldText.replaceRange(range.startOffset, range.endOffset, newContent)
        val newElement = RSCMElementFactory.createProperty(element.project, newText)
        return element.replace(newElement!!) as RSCMProperty
    }

    override fun getRangeInElement(element: RSCMProperty): TextRange {
        val key = element.getName() ?: return TextRange.EMPTY_RANGE
        val start = element.text.indexOf(key)
        return TextRange(start, start + key.length)
    }
}
