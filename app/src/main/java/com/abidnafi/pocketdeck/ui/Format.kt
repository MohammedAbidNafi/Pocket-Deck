package com.abidnafi.pocketdeck.ui

import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameSource
import com.abidnafi.pocketdeck.data.db.GameType
import java.util.concurrent.TimeUnit

fun formatPlaytime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return when {
        minutes < 1 -> "Less than a minute"
        minutes < 60 -> "$minutes min"
        else -> {
            val h = minutes / 60.0
            if (h < 10) "%.1f hours".format(h) else "${h.toInt()} hours"
        }
    }
}

fun formatLastPlayed(time: Long?, now: Long = System.currentTimeMillis()): String {
    if (time == null) return "Never played"
    val diff = now - time
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 2 -> "Just now"
        minutes < 60 -> "$minutes minutes ago"
        minutes < 24 * 60 -> "${minutes / 60} hours ago"
        days < 2 -> "Yesterday"
        days < 30 -> "$days days ago"
        days < 365 -> "${days / 30} months ago"
        else -> "Over a year ago"
    }
}

fun sourceLabel(game: GameEntity, platformName: String?): String = when (game.type) {
    GameType.ROM -> platformName ?: "ROM"
    GameType.EMULATOR -> "Emulator"
    GameType.APP -> when (game.source) {
        GameSource.PLAY_STORE -> "Android · Play Store"
        GameSource.SIDELOADED -> "Android · Installed app"
        else -> "Android · Added manually"
    }
}

fun gameSubtitle(game: GameEntity, platformName: String?): String = buildString {
    append(sourceLabel(game, platformName))
    if (game.lastPlayedAt != null) append(" · ").append(formatLastPlayed(game.lastPlayedAt))
    if (game.playTimeMs > 60_000) append(" · ").append(formatPlaytime(game.playTimeMs))
}

/** "1 game" / "3 games". */
fun gameCount(n: Int): String = if (n == 1) "1 game" else "$n games"
