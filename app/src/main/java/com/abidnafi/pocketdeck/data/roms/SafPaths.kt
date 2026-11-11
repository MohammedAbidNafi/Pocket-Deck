package com.abidnafi.pocketdeck.data.roms

/**
 * Maps Storage Access Framework document ids from the external-storage provider
 * (`primary:ROMs/snes/x.sfc`, `1A2B-3C4D:psx/y.chd`) to real file paths, which some
 * emulators (RetroArch, DraStic) need instead of content URIs.
 */
object SafPaths {
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    fun documentIdToPath(authority: String?, documentId: String): String? {
        if (documentId.startsWith("raw:")) return documentId.removePrefix("raw:")
        if (authority != null && authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        if (volume.isEmpty()) return null
        val relative = documentId.substringAfter(':')
        val root = when (volume) {
            "primary" -> "/storage/emulated/0"
            "home" -> "/storage/emulated/0/Documents"
            else -> "/storage/$volume"
        }
        return if (relative.isEmpty()) root else "$root/$relative"
    }
}
