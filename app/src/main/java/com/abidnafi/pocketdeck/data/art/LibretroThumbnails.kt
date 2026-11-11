package com.abidnafi.pocketdeck.data.art

import com.abidnafi.pocketdeck.data.roms.RomNames
import java.net.URLEncoder

/** Builds candidate image URLs on the free libretro-thumbnails server (no account needed). */
object LibretroThumbnails {
    enum class Kind(val folder: String) { BOXART("Named_Boxarts"), SNAP("Named_Snaps"), TITLE("Named_Titles") }

    private const val BASE = "https://thumbnails.libretro.com"
    private val forbidden = Regex("""[&*/:`<>?\\|"]""")
    private val regions = listOf("USA", "World", "Europe", "Japan")

    /** libretro replaces these characters with `_` in file names. */
    fun sanitize(name: String): String = name.replace(forbidden, "_")

    fun url(system: String, kind: Kind, name: String): String =
        "$BASE/${encode(system)}/${kind.folder}/${encode(sanitize(name))}.png"

    /**
     * Ordered guesses for one ROM: the exact file name first (matches No-Intro/Redump sets),
     * then the tag-less name with common regions.
     */
    fun candidates(system: String, kind: Kind, fileName: String): List<String> {
        val base = RomNames.baseName(fileName)
        val bare = RomNames.stripTags(base)
        val names = buildList {
            add(base)
            add(base.replace(Regex("""\s*\[[^\]]*\]"""), "").trim())
            regions.forEach { add("$bare ($it)") }
            add(bare)
        }
        return names.distinct().map { url(system, kind, it) }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
