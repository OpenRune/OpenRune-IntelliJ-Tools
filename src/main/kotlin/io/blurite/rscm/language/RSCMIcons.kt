package io.blurite.rscm.language

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader

/**
 * @author Chris
 * @since 12/4/2020
 */
object RSCMIcons {
    val FILE = IconLoader.getIcon("/icons/dictionary_dark.svg", this::class.java)
    // Icon for map-based properties (viewable but not a file) - using eye icon to indicate viewing
    val MAP = AllIcons.General.InspectionsEye
}
