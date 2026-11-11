package com.abidnafi.pocketdeck.data.model

import kotlinx.serialization.Serializable

/** A console / system that ROMs can belong to. Loaded from `assets/systems.json`. */
@Serializable
data class Platform(
    val id: String,
    val name: String,
    val shortName: String,
    /** `#RRGGBB` accent used for fallback artwork and chips. */
    val color: String,
    val extensions: List<String>,
    /** Folder names (normalised) that map a directory to this platform, ES-DE style. */
    val folders: List<String> = emptyList(),
    /** System folder name on thumbnails.libretro.com. */
    val libretro: String,
    /** RetroArch core basename, e.g. `snes9x` for `snes9x_libretro_android.so`. */
    val retroarchCore: String? = null,
) {
    /** Cartridge systems are commonly zipped; disc systems are not. */
    val acceptsArchives: Boolean get() = id !in DISC_PLATFORMS

    val argb: Long get() = 0xFF000000 or color.removePrefix("#").toLong(16)

    fun accepts(ext: String): Boolean =
        ext in extensions || (acceptsArchives && ext in ARCHIVE_EXTENSIONS)

    companion object {
        val ARCHIVE_EXTENSIONS = setOf("zip", "7z")
        private val DISC_PLATFORMS = setOf("psx", "ps2", "psp", "gc", "wii", "segacd", "saturn", "dreamcast", "3ds")
    }
}

/** Lookup helpers for deciding which platform a scanned file belongs to. Pure Kotlin so it is unit-testable. */
class PlatformRegistry(val platforms: List<Platform>) {
    private val byId = platforms.associateBy { it.id }
    private val byFolder: Map<String, Platform> = buildMap {
        for (p in platforms) {
            for (alias in p.folders + p.id + p.shortName + p.name) putIfAbsent(normalizeFolder(alias), p)
        }
    }
    private val byExtension: Map<String, List<Platform>> = buildMap<String, MutableList<Platform>> {
        for (p in platforms) for (ext in p.extensions) getOrPut(ext) { mutableListOf() }.add(p)
    }

    /** Every extension any platform can use, including archives. */
    val allExtensions: Set<String> = byExtension.keys + Platform.ARCHIVE_EXTENSIONS

    operator fun get(id: String?): Platform? = id?.let(byId::get)

    fun forFolderName(name: String): Platform? = byFolder[normalizeFolder(name)]

    /**
     * Resolves the platform of a file.
     * - [folderPlatform]: the platform the user assigned to the whole ROM folder, if any.
     * - [ancestors]: directory names from the scan root down to the file's parent.
     * Falls back to the extension only when it is unique to one platform.
     */
    fun resolve(ext: String, ancestors: List<String>, folderPlatform: String?): Platform? {
        val e = ext.lowercase()
        get(folderPlatform)?.let { return it.takeIf { p -> p.accepts(e) } }
        for (dir in ancestors.asReversed()) {
            val p = forFolderName(dir) ?: continue
            return p.takeIf { it.accepts(e) }
        }
        return byExtension[e]?.singleOrNull()
    }

    companion object {
        fun normalizeFolder(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
    }
}
