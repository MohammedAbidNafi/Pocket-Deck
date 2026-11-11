package com.abidnafi.pocketdeck.data.roms

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.abidnafi.pocketdeck.data.model.PlatformRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ScannedRom(
    val documentUri: String,
    val path: String?,
    val fileName: String,
    val platformId: String,
    val title: String,
)

/** Walks a user-picked SAF tree and finds ROM files. */
class RomScanner(private val context: Context, private val platforms: PlatformRegistry) {

    suspend fun scan(treeUri: Uri, folderPlatform: String?, onProgress: (found: Int) -> Unit = {}): List<ScannedRom> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ScannedRom>()
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            // The picked folder's own name counts too, so picking ".../ROMs/snes" maps to SNES.
            val rootName = DocumentFile.fromTreeUri(context, treeUri)?.name
            walk(treeUri, rootId, listOfNotNull(rootName), folderPlatform, depth = 0, results, onProgress)
            results
        }

    private suspend fun walk(
        tree: Uri,
        documentId: String,
        ancestors: List<String>,
        folderPlatform: String?,
        depth: Int,
        out: MutableList<ScannedRom>,
        onProgress: (Int) -> Unit,
    ) {
        coroutineContext.ensureActive()
        if (depth > MAX_DEPTH) return
        val files = mutableMapOf<String, String>() // name -> documentId
        val dirs = mutableListOf<Pair<String, String>>()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (PlatformRegistry.normalizeFolder(name) !in IGNORED_DIRS) dirs += name to id
                    } else if (RomNames.extension(name) in platforms.allExtensions) {
                        files[name] = id
                    }
                }
            }
        }

        val path = ancestors
        for (name in RomNames.selectEntries(files.keys.toList())) {
            val platform = platforms.resolve(RomNames.extension(name), path, folderPlatform) ?: continue
            val docId = files.getValue(name)
            out += ScannedRom(
                documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString(),
                path = SafPaths.documentIdToPath(tree.authority, docId),
                fileName = name,
                platformId = platform.id,
                title = RomNames.cleanTitle(name),
            )
        }
        onProgress(out.size)

        for ((name, id) in dirs) {
            walk(tree, id, path + name, folderPlatform, depth + 1, out, onProgress)
        }
    }

    private companion object {
        const val MAX_DEPTH = 6
        /** Front-end / emulator data folders that never contain games. */
        val IGNORED_DIRS = setOf(
            "bios", "system", "saves", "save", "savestates", "states", "media", "images", "covers",
            "boxart", "screenshots", "snaps", "videos", "manuals", "downloaded_images", "downloadedmedia",
            "cheats", "shaders", "thumbnails", "config", "android",
        )
    }
}
