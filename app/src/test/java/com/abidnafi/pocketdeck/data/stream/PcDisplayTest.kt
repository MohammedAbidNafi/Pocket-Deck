package com.abidnafi.pocketdeck.data.stream

import com.abidnafi.pocketdeck.data.stream.PcDisplay.Result
import org.junit.Assert.assertEquals
import org.junit.Test

/** Answers as the PC sends them (`Pocket-Deck-PC/docs/virtual-display.md`). */
class PcDisplayTest {

    @Test
    fun ready() {
        val body = """{"state":"ready","width":2400,"height":1080,"fps":120,"note":null,"leaseSeconds":90,"version":"0.2.0"}"""
        assertEquals(Result.Ready(null), PcDisplay.parseReply(200, body))
    }

    @Test
    fun readyWithANote() {
        val body = """{"state":"ready","width":1920,"height":1080,"fps":60,"note":"Nobody is signed in to Windows."}"""
        assertEquals(Result.Ready("Nobody is signed in to Windows."), PcDisplay.parseReply(200, body))
    }

    @Test
    fun unavailable() {
        val body = """{"state":"unavailable","message":"The virtual display isn't set up on this PC."}"""
        assertEquals(Result.Unavailable, PcDisplay.parseReply(503, body))
        assertEquals(Result.Unavailable, PcDisplay.parseReply(404, """{"state":"failed","message":"Not found."}"""))
    }

    @Test
    fun failed() {
        val body = """{"state":"failed","message":"The virtual display didn't start in time."}"""
        assertEquals(Result.Failed("The virtual display didn't start in time."), PcDisplay.parseReply(500, body))
        assertEquals(Result.Failed("The PC answered with HTTP 502."), PcDisplay.parseReply(502, "<html>"))
    }
}
