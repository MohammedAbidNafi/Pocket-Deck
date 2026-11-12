package com.abidnafi.pocketdeck.data.stream

import com.abidnafi.pocketdeck.data.db.StreamHostEntity
import java.net.URLDecoder

/**
 * The "pair with one scan" code the PocketDeck PC app shows as a QR code (also a deep link):
 *
 * `pocketdeck://pair?v=1&n=<pc name>&a=<addr>[,<addr>...]&p=<http port>&pin=<4 digits>&fp=<cert sha256>`
 *
 * The PC enters [pin] into Sunshine itself when our pairing request arrives, and [fingerprint] lets us
 * check that the PC that answered is the one whose screen showed the code. Contract:
 * `Pocket-Deck-PC/docs/pair-with-scan.md` (the source of truth; change it there first).
 */
data class PairCode(
    val name: String,
    /** LAN addresses, best first. */
    val addresses: List<String>,
    val httpPort: Int,
    val pin: String,
    /** SHA-256 of the host's server certificate (DER), lowercase hex. */
    val fingerprint: String,
) {
    sealed interface Parsed {
        data class Ok(val code: PairCode) : Parsed
        /** Some other QR code / link: keep scanning. */
        data object NotPairCode : Parsed
        /** One of ours, but unusable; [message] is for the user. */
        data class Invalid(val message: String) : Parsed
    }

    companion object {
        const val SCHEME = "pocketdeck"
        const val HOST = "pair"
        const val VERSION = "1"

        private val PIN = Regex("^[0-9]{4}$")
        private val FINGERPRINT = Regex("^[0-9a-f]{64}$")
        private const val BROKEN = "This pairing code is incomplete. Show a new one on the PC and scan again."

        fun parse(text: String): Parsed {
            val s = text.trim()
            val prefix = "$SCHEME://$HOST"
            if (!s.startsWith(prefix, ignoreCase = true)) return Parsed.NotPairCode
            val rest = s.substring(prefix.length).removePrefix("/")
            if (rest.isNotEmpty() && !rest.startsWith("?")) return Parsed.NotPairCode
            val query = rest.removePrefix("?").substringBefore('#')

            // First value wins; unknown keys (e.g. the reserved remote-play "r") are ignored.
            val params = mutableMapOf<String, String>()
            for (part in query.split('&')) {
                if (part.isEmpty()) continue
                val key = decode(part.substringBefore('=')) ?: return Parsed.Invalid(BROKEN)
                val value = decode(part.substringAfter('=', "")) ?: return Parsed.Invalid(BROKEN)
                params.putIfAbsent(key, value)
            }

            if (params["v"] != VERSION) {
                return Parsed.Invalid("This code is from a newer PocketDeck on the PC. Update PocketDeck on your phone.")
            }
            val pin = params["pin"]?.takeIf { PIN.matches(it) } ?: return Parsed.Invalid(BROKEN)
            val fp = params["fp"]?.lowercase()?.takeIf { FINGERPRINT.matches(it) } ?: return Parsed.Invalid(BROKEN)
            val port = when (val p = params["p"]) {
                null, "" -> StreamHostEntity.DEFAULT_HTTP_PORT
                else -> p.toIntOrNull()?.takeIf { it in 1..65535 } ?: return Parsed.Invalid(BROKEN)
            }
            val addresses = params["a"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() && it.none(Char::isWhitespace) }
            if (addresses.isEmpty()) return Parsed.Invalid(BROKEN)
            val name = params["n"]?.trim()?.takeIf { it.isNotEmpty() } ?: "your PC"
            return Parsed.Ok(PairCode(name, addresses.distinct(), port, pin, fp))
        }

        private fun decode(s: String): String? = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrNull()
    }
}
