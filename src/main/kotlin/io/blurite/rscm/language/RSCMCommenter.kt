package io.blurite.rscm.language

import com.intellij.lang.Commenter

class RSCMCommenter : Commenter {
    override fun getLineCommentPrefix() = "#"

    override fun getBlockCommentPrefix() = ""

    override fun getBlockCommentSuffix() = null

    override fun getCommentedBlockCommentPrefix() = null

    override fun getCommentedBlockCommentSuffix() = null
}
