package com.abidnafi.pocketdeck.data.roms

/** Turns No-Intro / Redump / GoodTools file names into display titles. Pure Kotlin. */
object RomNames {
    private val tagGroups = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val discTag = Regex("""\((?:disc|disk|cd)\s*([0-9]+|[a-d])(?:\s*of\s*[0-9]+)?\)""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")
    private val trailingArticle = Regex("""^(.*),\s*(The|A|An)$""", RegexOption.IGNORE_CASE)

    fun extension(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    fun baseName(fileName: String): String =
        if ('.' in fileName) fileName.substringBeforeLast('.') else fileName

    /** `"Legend of Zelda, The - A Link to the Past (USA) [!].sfc"` -> `"The Legend of Zelda - A Link to the Past"`. */
    fun cleanTitle(fileName: String): String {
        val stripped = stripTags(baseName(fileName).replace('_', ' '))
        val parts = stripped.split(" - ").map { segment ->
            trailingArticle.matchEntire(segment.trim())?.let { "${it.groupValues[2]} ${it.groupValues[1]}" } ?: segment.trim()
        }
        return parts.joinToString(" - ").ifBlank { baseName(fileName) }
    }

    /** Removes `(…)` and `[…]` groups and normalises whitespace, keeping the original word order. */
    fun stripTags(name: String): String =
        name.replace(tagGroups, "").replace(whitespace, " ").trim()

    /** Disc number from tags like `(Disc 2)` / `(Disc B)`; null when there is no disc tag. */
    fun discNumber(fileName: String): Int? {
        val v = discTag.find(fileName)?.groupValues?.get(1) ?: return null
        return v.toIntOrNull() ?: (v.lowercase()[0] - 'a' + 1)
    }

    fun sortTitle(title: String): String {
        val t = title.lowercase().trim()
        return listOf("the ", "a ", "an ").firstOrNull { t.startsWith(it) }?.let { t.removePrefix(it) } ?: t
    }

    /**
     * Picks which files in one directory should become library entries:
     * drops track `.bin` files next to a `.cue`/`.gdi`, drops discs covered by an `.m3u`,
     * and keeps only the first disc of multi-disc sets otherwise.
     */
    fun selectEntries(fileNames: List<String>): List<String> {
        val exts = fileNames.associateWith { extension(it) }
        val hasSheet = exts.values.any { it == "cue" || it == "gdi" || it == "ccd" }
        val playlists = fileNames.filter { exts[it] == "m3u" }.map { cleanTitle(it).lowercase() }.toSet()

        val candidates = fileNames.filter { name ->
            val ext = exts[name]
            when {
                ext == "bin" && hasSheet -> false
                ext != "m3u" && discNumber(name) != null && cleanTitle(name).lowercase() in playlists -> false
                else -> true
            }
        }
        // Collapse "(Disc N)" siblings to the lowest disc.
        val (multi, single) = candidates.partition { discNumber(it) != null }
        val firstDiscs = multi
            .groupBy { cleanTitle(it).lowercase() to extension(it) }
            .values.map { group -> group.minBy { discNumber(it)!! } }
        return (single + firstDiscs).sorted()
    }
}
