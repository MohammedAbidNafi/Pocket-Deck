package com.abidnafi.pocketdeck.data.stream

import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameSource
import com.abidnafi.pocketdeck.data.db.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAppSyncTest {
    private val uuid = "ABCD-1234"

    private fun existing(id: Long, appId: Int, title: String, favorite: Boolean = false) = GameEntity(
        id = id, key = GameEntity.streamKey(uuid, appId), type = GameType.STREAM, title = title,
        sortTitle = title.lowercase(), source = GameSource.PC_HOST, autoDetected = true, addedAt = 1,
        hostId = 7, streamAppId = appId, favorite = favorite, playTimeMs = 5_000,
    )

    @Test
    fun `stream key is stable per host and app`() {
        assertEquals("pc:ABCD-1234:881448767", GameEntity.streamKey(uuid, 881448767))
    }

    @Test
    fun `new apps are inserted as stream games of the host`() {
        val d = StreamAppSync.diff(7, uuid, emptyList(), listOf(HostApp(1, "Desktop"), HostApp(2, "The Witcher 3")), now = 100)
        assertEquals(2, d.inserts.size)
        val witcher = d.inserts.first { it.streamAppId == 2 }
        assertEquals(GameType.STREAM, witcher.type)
        assertEquals(GameSource.PC_HOST, witcher.source)
        assertEquals(7L, witcher.hostId)
        assertEquals("witcher 3", witcher.sortTitle)
        assertEquals(100L, witcher.addedAt)
        assertTrue(d.updates.isEmpty() && d.deleteIds.isEmpty())
    }

    @Test
    fun `renamed apps are updated and keep user data`() {
        val old = existing(10, 2, "Witcher", favorite = true)
        val d = StreamAppSync.diff(7, uuid, listOf(old), listOf(HostApp(2, "The Witcher 3")), now = 100)
        val updated = d.updates.single()
        assertEquals("The Witcher 3", updated.title)
        assertTrue(updated.favorite)
        assertEquals(5_000L, updated.playTimeMs)
        assertEquals(10L, updated.id)
        assertTrue(d.inserts.isEmpty())
    }

    @Test
    fun `unchanged apps produce no writes`() {
        val d = StreamAppSync.diff(7, uuid, listOf(existing(10, 2, "Portal 2")), listOf(HostApp(2, "Portal 2")), now = 100)
        assertTrue(d.inserts.isEmpty() && d.updates.isEmpty() && d.deleteIds.isEmpty())
    }

    @Test
    fun `apps removed from the host are deleted`() {
        val d = StreamAppSync.diff(7, uuid, listOf(existing(10, 2, "Portal 2"), existing(11, 3, "Steam")), listOf(HostApp(3, "Steam")), now = 1)
        assertEquals(listOf(10L), d.deleteIds)
    }

    @Test
    fun `duplicate and blank names are handled`() {
        val d = StreamAppSync.diff(7, uuid, emptyList(), listOf(HostApp(5, "  "), HostApp(5, "Dup")), now = 1)
        assertEquals("App 5", d.inserts.single().title)
    }

    @Test
    fun `addresses parse with and without ports`() {
        assertEquals("192.168.1.20" to 47989, HostRepository.parseAddress("192.168.1.20"))
        assertEquals("192.168.1.20" to 48000, HostRepository.parseAddress(" 192.168.1.20:48000 "))
        assertEquals("gaming-pc.local" to 47989, HostRepository.parseAddress("http://gaming-pc.local/"))
        assertEquals("fe80::1" to 47989, HostRepository.parseAddress("[fe80::1]"))
        assertEquals("fe80::1" to 50000, HostRepository.parseAddress("[fe80::1]:50000"))
        assertEquals("fe80::1" to 47989, HostRepository.parseAddress("fe80::1"))
        assertNull(HostRepository.parseAddress(""))
        assertNull(HostRepository.parseAddress("pc:99999"))
        assertNull(HostRepository.parseAddress("my pc"))
    }
}
