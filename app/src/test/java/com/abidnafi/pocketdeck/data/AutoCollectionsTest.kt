package com.abidnafi.pocketdeck.data

import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameSource
import com.abidnafi.pocketdeck.data.db.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCollectionsTest {
    private var nextId = 1L

    private fun game(
        title: String,
        type: GameType,
        platform: String? = null,
        host: Long? = null,
        favorite: Boolean = false,
        hidden: Boolean = false,
        plays: Int = 0,
        lastPlayed: Long? = null,
        playTime: Long = 0,
        added: Long = 0,
    ) = GameEntity(
        id = nextId++, key = "k$nextId", type = type, title = title, sortTitle = title.lowercase(),
        platformId = platform, source = GameSource.MANUAL, addedAt = added, hostId = host,
        favorite = favorite, hidden = hidden, playCount = plays, lastPlayedAt = lastPlayed, playTimeMs = playTime,
    )

    private val platforms = listOf("snes" to "Super Nintendo", "psx" to "PlayStation", "gba" to "Game Boy Advance")

    @Test
    fun `builds one collection per kind, in order, skipping empty ones`() {
        val games = listOf(
            game("Celeste", GameType.APP, favorite = true, plays = 3),
            game("Mario World", GameType.ROM, platform = "snes"),
            game("Crash", GameType.ROM, platform = "psx", plays = 1),
            game("Witcher 3", GameType.STREAM, host = 1),
            game("RetroArch", GameType.EMULATOR),
        )
        val ids = AutoCollections.build(games, platforms, listOf(AutoCollectionHost(1, "Desk PC"))).map { it.id }
        assertEquals(
            listOf(
                AutoCollections.FAVOURITES, AutoCollections.ANDROID, AutoCollections.PC,
                AutoCollections.platformId("snes"), AutoCollections.platformId("psx"),
                AutoCollections.EMULATORS, AutoCollections.UNPLAYED,
            ),
            ids,
        )
    }

    @Test
    fun `hidden games are left out and emulators are never unplayed`() {
        val games = listOf(
            game("Hidden one", GameType.APP, hidden = true),
            game("RetroArch", GameType.EMULATOR),
        )
        val built = AutoCollections.build(games, platforms, emptyList())
        assertTrue(built.none { it.id == AutoCollections.ANDROID })
        assertTrue(built.none { it.id == AutoCollections.UNPLAYED })
    }

    @Test
    fun `several PCs get one collection each`() {
        val games = listOf(game("A", GameType.STREAM, host = 1), game("B", GameType.STREAM, host = 2))
        val built = AutoCollections.build(games, platforms, listOf(AutoCollectionHost(1, "Desk"), AutoCollectionHost(2, "Laptop")))
        assertEquals(listOf("Desk", "Laptop"), built.filter { it.id.startsWith("auto:pc") }.map { it.name })
    }

    @Test
    fun `one PC is named after the host`() {
        val built = AutoCollections.build(listOf(game("A", GameType.STREAM, host = 1)), platforms, listOf(AutoCollectionHost(1, "Desk")))
        val pc = built.single { it.id == AutoCollections.PC }
        assertEquals("PC games", pc.name)
        assertEquals("Streamed from Desk", pc.subtitle)
    }

    @Test
    fun `game sorts order as labelled with name as tie-break`() {
        val a = game("Alpha", GameType.APP, lastPlayed = 5, playTime = 10, added = 1)
        val b = game("Bravo", GameType.APP, lastPlayed = 9, playTime = 10, added = 3)
        val c = game("Charlie", GameType.APP, lastPlayed = null, playTime = 99, added = 2)
        val list = listOf(c, b, a)
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), GameSort.NAME.apply(list).map { it.title })
        assertEquals(listOf("Bravo", "Alpha", "Charlie"), GameSort.RECENT.apply(list).map { it.title })
        assertEquals(listOf("Charlie", "Alpha", "Bravo"), GameSort.PLAYTIME.apply(list).map { it.title })
        assertEquals(listOf("Bravo", "Charlie", "Alpha"), GameSort.ADDED.apply(list).map { it.title })
        // Sorting by when a game joined a collection instead of the library.
        val joined = mapOf(a.id to 100L, b.id to 50L, c.id to 75L)
        assertEquals(listOf("Alpha", "Charlie", "Bravo"), GameSort.ADDED.apply(list) { joined.getValue(it.id) }.map { it.title })
    }

    @Test
    fun `sorts cycle through every option`() {
        assertEquals(GameSort.NAME, GameSort.entries.fold(GameSort.NAME) { s, _ -> s.next() })
        assertEquals(CollectionSort.NAME, CollectionSort.entries.fold(CollectionSort.NAME) { s, _ -> s.next() })
    }
}
