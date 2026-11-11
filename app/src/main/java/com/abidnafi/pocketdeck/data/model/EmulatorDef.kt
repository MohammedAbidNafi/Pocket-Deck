package com.abidnafi.pocketdeck.data.model

import kotlinx.serialization.Serializable

/**
 * How to start a standalone emulator with a ROM. Built-ins come from `assets/emulators.json`;
 * user-made ones are stored in settings with [custom] = true.
 *
 * Template tokens usable in [data] and [extras] values:
 * `{rom_uri}` (content:// URI), `{rom_path}` (real file path), `{package}`, `{core}` (RetroArch core).
 */
@Serializable
data class EmulatorDef(
    val id: String,
    val name: String,
    val packages: List<String>,
    val platforms: List<String> = emptyList(),
    /** Fully-qualified activity class; when null the package's launch intent action is used. */
    val activity: String? = null,
    val action: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val extras: Map<String, String> = emptyMap(),
    /** `clear_task`, `clear_top`, `no_history`. */
    val flags: List<String> = emptyList(),
    val needsFilePath: Boolean = false,
    /** Shown as a tile only; can't launch ROMs directly. */
    val standaloneOnly: Boolean = false,
    val note: String? = null,
    val custom: Boolean = false,
) {
    fun supports(platformId: String): Boolean = !standaloneOnly && platformId in platforms

    companion object {
        /** Pseudo emulator: hand the ROM to Android's "Open with" chooser. */
        const val SYSTEM_CHOOSER = "system_chooser"
    }
}
