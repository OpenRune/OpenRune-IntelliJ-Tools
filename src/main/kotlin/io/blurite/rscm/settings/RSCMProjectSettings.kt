package io.blurite.rscm.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "RSCMProjectSettings",
    storages = [Storage("rscmProjectSettings.xml")],
)
class RSCMProjectSettings(
    private val project: Project,
) : PersistentStateComponent<RSCMProjectSettingsState> {
    private var state = RSCMProjectSettingsState()

    override fun getState(): RSCMProjectSettingsState = state

    override fun loadState(state: RSCMProjectSettingsState) {
        // Migrate old mappingsPath to mappingsPaths if needed
        if (state.mappingsPaths.isEmpty() && state.mappingsPath.isNotEmpty()) {
            state.mappingsPaths.add(state.mappingsPath)
            state.mappingsPath = "" // Clear after migration
        }
        this.state = state
    }

    var mappingsPaths: MutableList<String>
        get() = state.mappingsPaths
        set(value) {
            state.mappingsPaths = value
        }

    // Backward compatibility: get first path or empty string
    @Deprecated("Use mappingsPaths instead")
    var mappingsPath: String
        get() = state.mappingsPaths.firstOrNull() ?: ""
        set(value) {
            if (value.isNotEmpty()) {
                if (state.mappingsPaths.isEmpty()) {
                    state.mappingsPaths.add(value)
                } else {
                    state.mappingsPaths[0] = value
                }
            }
        }

    var referentialMappings: String
        get() = state.referentialMappings
        set(value) {
            state.referentialMappings = value
        }

    var enableFileProvider: Boolean
        get() = state.enableFileProvider
        set(value) {
            state.enableFileProvider = value
        }

    var enableAlterConstantProvider: Boolean
        get() = state.enableAlterConstantProvider
        set(value) {
            state.enableAlterConstantProvider = value
        }

    var settingsFilePath: String
        get() = state.settingsFilePath
        set(value) {
            state.settingsFilePath = value
        }
    
    /**
     * Get effective settings, either from settings file or from IntelliJ settings.
     */
    fun getEffectiveSettings(): EffectiveSettings {
        val settingsFile = settingsFilePath
        if (settingsFile.isNotEmpty()) {
            val parsed = RSCMSettingsFileParser.parseSettingsFile(settingsFile)
            if (parsed != null) {
                return EffectiveSettings(
                    mappingsPaths = parsed.mappingsDirectories.toMutableList(),
                    enableFileProvider = parsed.enableFileProvider,
                    enableAlterConstantProvider = parsed.enableAlterConstantProvider,
                    referentialMappings = parsed.referentialMappings,
                    isFromSettingsFile = true
                )
            }
        }
        
        return EffectiveSettings(
            mappingsPaths = state.mappingsPaths,
            enableFileProvider = state.enableFileProvider,
            enableAlterConstantProvider = state.enableAlterConstantProvider,
            referentialMappings = state.referentialMappings,
            isFromSettingsFile = false
        )
    }
    
    data class EffectiveSettings(
        val mappingsPaths: MutableList<String>,
        val enableFileProvider: Boolean,
        val enableAlterConstantProvider: Boolean,
        val referentialMappings: String,
        val isFromSettingsFile: Boolean
    )

    companion object {
        fun getInstance(project: Project): RSCMProjectSettings = project.getService(RSCMProjectSettings::class.java)
    }
}
