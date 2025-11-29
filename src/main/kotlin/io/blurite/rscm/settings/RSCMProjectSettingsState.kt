package io.blurite.rscm.settings

data class RSCMProjectSettingsState(
    var settingsFilePath: String = "",
    var mappingsPaths: MutableList<String> = mutableListOf(),
    // Migration field: keep for backward compatibility with old XML files
    @Deprecated("Use mappingsPaths instead")
    var mappingsPath: String = "",
    var referentialMappings: String = "",
    var enableFileProvider: Boolean = true,
    var enableAlterConstantProvider: Boolean = true,
)
