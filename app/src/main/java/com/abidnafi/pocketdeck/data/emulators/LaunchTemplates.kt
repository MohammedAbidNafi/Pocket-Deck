package com.abidnafi.pocketdeck.data.emulators

import com.abidnafi.pocketdeck.data.model.EmulatorDef

/** Everything needed to build the Android Intent, kept free of Android types for unit tests. */
data class LaunchSpec(
    val packageName: String,
    val className: String?,
    val action: String,
    val data: String?,
    val mimeType: String?,
    val extras: Map<String, String>,
    val flags: Set<String>,
    /** The content URI the emulator must be granted access to. */
    val grantUri: String,
)

sealed interface SpecResult {
    data class Ok(val spec: LaunchSpec) : SpecResult
    data class Error(val message: String) : SpecResult
}

object LaunchTemplates {
    private val token = Regex("""\{(\w+)\}""")

    fun fill(template: String, vars: Map<String, String>): String =
        template.replace(token) { m -> vars[m.groupValues[1]] ?: m.value }

    fun build(
        def: EmulatorDef,
        installedPackage: String,
        romUri: String,
        romPath: String?,
        core: String?,
    ): SpecResult {
        val templates = listOfNotNull(def.data) + def.extras.values
        val usesPath = def.needsFilePath || templates.any { "{rom_path}" in it }
        if (usesPath && romPath == null) {
            return SpecResult.Error(
                "${def.name} needs a real file path, but this ROM's folder is provided by an app or " +
                    "cloud storage. Move it to internal storage or an SD card and rescan.",
            )
        }
        if (templates.any { "{core}" in it } && core == null) {
            return SpecResult.Error("No RetroArch core is configured for this system.")
        }
        val vars = buildMap {
            put("rom_uri", romUri)
            romPath?.let { put("rom_path", it) }
            put("package", installedPackage)
            core?.let { put("core", it) }
        }
        return SpecResult.Ok(
            LaunchSpec(
                packageName = installedPackage,
                className = def.activity,
                action = def.action ?: "android.intent.action.VIEW",
                data = def.data?.let { fill(it, vars) },
                mimeType = def.mimeType,
                extras = def.extras.mapValues { fill(it.value, vars) },
                flags = def.flags.toSet(),
                grantUri = romUri,
            ),
        )
    }
}
