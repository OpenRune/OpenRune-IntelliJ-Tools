package io.blurite.rscm.language.util

import com.intellij.openapi.project.Project
import io.blurite.rscm.language.RSCMUtil
import io.blurite.rscm.language.annotator.RSCMAnnotator

/**
 * @author Chris | 5/4/21
 */
val String.hasRscmSeparator: Boolean
    get() = contains(RSCMAnnotator.RSCM_SEPARATOR_STR)

val String.rscmPrefix: String
    get() = substringBefore(RSCMAnnotator.RSCM_SEPARATOR_STR)

fun String.isValidRscmPrefix(project: Project) = RSCMUtil.isValidPrefix(project, this)

fun String.remove(oldValue: String) = replace(oldValue, "")

operator fun String.minus(oldValue: String) = replace(oldValue, "")

operator fun String.minus(oldValue: Char) = replace(oldValue.toString(), "")

fun String.toMap(
    pairDelimiter: String = ",",
    keyValueDelimiter: String = "=",
): Map<String, String> =
    this
        .split(pairDelimiter)
        .mapNotNull { entry ->
            val parts = entry.trim().split(keyValueDelimiter, limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
