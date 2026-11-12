package com.abidnafi.pocketdeck.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairCodeTest {
    private val fp = "ab".repeat(32)

    private fun ok(text: String) = (PairCode.parse(text) as PairCode.Parsed.Ok).code

    @Test
    fun parsesFullCode() {
        val c = ok("pocketdeck://pair?v=1&n=Gaming%20PC&a=192.168.1.20,10.0.0.5&p=48000&pin=0123&fp=$fp")
        assertEquals("Gaming PC", c.name)
        assertEquals(listOf("192.168.1.20", "10.0.0.5"), c.addresses)
        assertEquals(48000, c.httpPort)
        assertEquals("0123", c.pin)
        assertEquals(fp, c.fingerprint)
    }

    @Test
    fun defaultsPortAndIgnoresUnknownKeys() {
        val c = ok("pocketdeck://pair?v=1&n=PC&a=192.168.1.20&pin=9999&fp=${fp.uppercase()}&r=somenodeid&x=")
        assertEquals(47989, c.httpPort)
        assertEquals(fp, c.fingerprint)
    }

    @Test
    fun plusDecodesToSpace() {
        assertEquals("My PC", ok("pocketdeck://pair?v=1&n=My+PC&a=1.2.3.4&pin=1234&fp=$fp").name)
    }

    @Test
    fun otherCodesAreNotOurs() {
        assertTrue(PairCode.parse("https://example.com") is PairCode.Parsed.NotPairCode)
        assertTrue(PairCode.parse("pocketdeck://pairing?v=1") is PairCode.Parsed.NotPairCode)
        assertTrue(PairCode.parse("hello") is PairCode.Parsed.NotPairCode)
    }

    @Test
    fun rejectsOtherVersions() {
        val r = PairCode.parse("pocketdeck://pair?v=2&n=PC&a=1.2.3.4&pin=1234&fp=$fp")
        assertTrue(r is PairCode.Parsed.Invalid && r.message.contains("Update PocketDeck"))
        assertTrue(PairCode.parse("pocketdeck://pair?n=PC&a=1.2.3.4&pin=1234&fp=$fp") is PairCode.Parsed.Invalid)
    }

    @Test
    fun rejectsBadFields() {
        val base = "pocketdeck://pair?v=1&n=PC"
        assertTrue(PairCode.parse("$base&a=1.2.3.4&pin=123&fp=$fp") is PairCode.Parsed.Invalid)
        assertTrue(PairCode.parse("$base&a=1.2.3.4&pin=12a4&fp=$fp") is PairCode.Parsed.Invalid)
        assertTrue(PairCode.parse("$base&a=1.2.3.4&pin=1234&fp=abc") is PairCode.Parsed.Invalid)
        assertTrue(PairCode.parse("$base&a=1.2.3.4&pin=1234&fp=${"zz".repeat(32)}") is PairCode.Parsed.Invalid)
        assertTrue(PairCode.parse("$base&a=&pin=1234&fp=$fp") is PairCode.Parsed.Invalid)
        assertTrue(PairCode.parse("$base&a=1.2.3.4&p=70000&pin=1234&fp=$fp") is PairCode.Parsed.Invalid)
    }
}
