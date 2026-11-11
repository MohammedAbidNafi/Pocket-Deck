package com.abidnafi.pocketdeck.data.emulators

import com.abidnafi.pocketdeck.data.apps.InstalledApps
import com.abidnafi.pocketdeck.data.model.EmulatorDef

/** Built-in emulator definitions plus the user's custom ones. */
class EmulatorRegistry(
    private val builtIn: List<EmulatorDef>,
    private val installedApps: InstalledApps,
) {
    @Volatile
    var custom: List<EmulatorDef> = emptyList()

    val all: List<EmulatorDef> get() = custom + builtIn

    operator fun get(id: String?): EmulatorDef? = when (id) {
        null -> null
        EmulatorDef.SYSTEM_CHOOSER -> systemChooser
        else -> all.firstOrNull { it.id == id }
    }

    fun allPackages(): Set<String> = all.flatMap { it.packages }.toSet()

    fun forPackage(pkg: String): EmulatorDef? = all.firstOrNull { pkg in it.packages }

    /** First package of [def] that is installed, preferring the order in the definition. */
    fun installedPackage(def: EmulatorDef): String? = def.packages.firstOrNull { installedApps.isInstalled(it) }

    fun isInstalled(def: EmulatorDef): Boolean = installedPackage(def) != null

    /** Emulators able to run [platformId], installed ones first. */
    fun forPlatform(platformId: String): List<EmulatorDef> =
        all.filter { it.supports(platformId) }.sortedByDescending { isInstalled(it) }

    fun defaultFor(platformId: String, configured: String?): EmulatorDef? =
        get(configured)?.takeIf { it.id == EmulatorDef.SYSTEM_CHOOSER || isInstalled(it) }
            ?: forPlatform(platformId).firstOrNull { isInstalled(it) }

    companion object {
        val systemChooser = EmulatorDef(
            id = EmulatorDef.SYSTEM_CHOOSER,
            name = "Ask every time (Open with…)",
            packages = emptyList(),
        )
    }
}
