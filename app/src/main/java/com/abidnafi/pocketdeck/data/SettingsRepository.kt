package com.abidnafi.pocketdeck.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abidnafi.pocketdeck.data.model.EmulatorDef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class GlyphStyle { XBOX, PLAYSTATION, NINTENDO }

enum class TileSize { SMALL, MEDIUM, LARGE }

/** Shape of game tiles: circles, squares, or the classic rectangles (wide / box-art). */
enum class TileShape { ROUND, SQUARE, RECTANGLE }

data class Settings(
    val onboardingDone: Boolean = false,
    val glyphStyle: GlyphStyle = GlyphStyle.XBOX,
    /** Nintendo-style confirm on the right face button. */
    val swapAB: Boolean = false,
    val uiSounds: Boolean = true,
    val haptics: Boolean = true,
    val accentIndex: Int = 0,
    val tileSize: TileSize = TileSize.MEDIUM,
    val tileShape: TileShape = TileShape.RECTANGLE,
    val onlineArtwork: Boolean = true,
    val wifiOnlyArtwork: Boolean = false,
    val autoDetectGames: Boolean = true,
    val showClock: Boolean = true,
    /** platformId -> emulator id. */
    val defaultEmulators: Map<String, String> = emptyMap(),
    val customEmulators: List<EmulatorDef> = emptyList(),
    /** "Back to PocketDeck" notification while a launched game runs. Opt-in. */
    val returnNotification: Boolean = false,
    /** Home shelf ids in the user's order (see HomeRows); rows not listed keep their default spot. */
    val homeRowOrder: List<String> = emptyList(),
    /** Home shelf ids the user has hidden. */
    val hiddenHomeRows: Set<String> = emptySet(),
)

private val Context.dataStore by preferencesDataStore("settings")

/** Separates home row ids in the saved order (ids never contain it). */
private const val ROW_SEPARATOR = "|"

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val glyphStyle = stringPreferencesKey("glyph_style")
        val swapAB = booleanPreferencesKey("swap_ab")
        val uiSounds = booleanPreferencesKey("ui_sounds")
        val haptics = booleanPreferencesKey("haptics")
        val accent = intPreferencesKey("accent")
        val tileSize = stringPreferencesKey("tile_size")
        val tileShape = stringPreferencesKey("tile_shape")
        val onlineArt = booleanPreferencesKey("online_art")
        val wifiOnlyArt = booleanPreferencesKey("wifi_only_art")
        val autoDetect = booleanPreferencesKey("auto_detect")
        val showClock = booleanPreferencesKey("show_clock")
        val defaultEmulators = stringPreferencesKey("default_emulators")
        val customEmulators = stringPreferencesKey("custom_emulators")
        val returnNotification = booleanPreferencesKey("return_notification")
        val homeRowOrder = stringPreferencesKey("home_row_order")
        val hiddenHomeRows = stringSetPreferencesKey("home_rows_hidden")
        val sessionGame = longPreferencesKey("session_game")
        val sessionStart = longPreferencesKey("session_start")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val emulatorsSerializer = ListSerializer(EmulatorDef.serializer())

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[Keys.onboardingDone] ?: false,
        glyphStyle = enumOr(this[Keys.glyphStyle], GlyphStyle.XBOX),
        swapAB = this[Keys.swapAB] ?: false,
        uiSounds = this[Keys.uiSounds] ?: true,
        haptics = this[Keys.haptics] ?: true,
        accentIndex = this[Keys.accent] ?: 0,
        tileSize = enumOr(this[Keys.tileSize], TileSize.MEDIUM),
        tileShape = enumOr(this[Keys.tileShape], TileShape.RECTANGLE),
        onlineArtwork = this[Keys.onlineArt] ?: true,
        wifiOnlyArtwork = this[Keys.wifiOnlyArt] ?: false,
        autoDetectGames = this[Keys.autoDetect] ?: true,
        showClock = this[Keys.showClock] ?: true,
        defaultEmulators = this[Keys.defaultEmulators]
            ?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() } ?: emptyMap(),
        customEmulators = this[Keys.customEmulators]
            ?.let { runCatching { json.decodeFromString(emulatorsSerializer, it) }.getOrNull() } ?: emptyList(),
        returnNotification = this[Keys.returnNotification] ?: false,
        homeRowOrder = this[Keys.homeRowOrder]?.split(ROW_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList(),
        hiddenHomeRows = this[Keys.hiddenHomeRows] ?: emptySet(),
    )

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.onboardingDone] = done }
    suspend fun setGlyphStyle(style: GlyphStyle) = edit { it[Keys.glyphStyle] = style.name }
    suspend fun setSwapAB(value: Boolean) = edit { it[Keys.swapAB] = value }
    suspend fun setUiSounds(value: Boolean) = edit { it[Keys.uiSounds] = value }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.haptics] = value }
    suspend fun setAccent(index: Int) = edit { it[Keys.accent] = index }
    suspend fun setTileSize(size: TileSize) = edit { it[Keys.tileSize] = size.name }
    suspend fun setTileShape(shape: TileShape) = edit { it[Keys.tileShape] = shape.name }
    suspend fun setOnlineArtwork(value: Boolean) = edit { it[Keys.onlineArt] = value }
    suspend fun setWifiOnlyArtwork(value: Boolean) = edit { it[Keys.wifiOnlyArt] = value }
    suspend fun setAutoDetect(value: Boolean) = edit { it[Keys.autoDetect] = value }
    suspend fun setShowClock(value: Boolean) = edit { it[Keys.showClock] = value }
    suspend fun setHomeRowOrder(ids: List<String>) = edit { it[Keys.homeRowOrder] = ids.joinToString(ROW_SEPARATOR) }
    suspend fun setHomeRowHidden(id: String, hidden: Boolean) = edit { prefs ->
        val set = prefs[Keys.hiddenHomeRows].orEmpty()
        prefs[Keys.hiddenHomeRows] = if (hidden) set + id else set - id
    }
    suspend fun resetHomeRows() = edit { it.remove(Keys.homeRowOrder); it.remove(Keys.hiddenHomeRows) }
    suspend fun setReturnNotification(value: Boolean) = edit { it[Keys.returnNotification] = value }

    suspend fun setDefaultEmulator(platformId: String, emulatorId: String?) = edit { prefs ->
        val map = prefs.toSettings().defaultEmulators.toMutableMap()
        if (emulatorId == null) map.remove(platformId) else map[platformId] = emulatorId
        prefs[Keys.defaultEmulators] = json.encodeToString(mapSerializer, map)
    }

    suspend fun saveCustomEmulator(def: EmulatorDef) = edit { prefs ->
        val list = prefs.toSettings().customEmulators.filterNot { it.id == def.id } + def.copy(custom = true)
        prefs[Keys.customEmulators] = json.encodeToString(emulatorsSerializer, list)
    }

    suspend fun deleteCustomEmulator(id: String) = edit { prefs ->
        val list = prefs.toSettings().customEmulators.filterNot { it.id == id }
        prefs[Keys.customEmulators] = json.encodeToString(emulatorsSerializer, list)
    }

    /** A game launched from here that hasn't returned yet; survives process death. */
    suspend fun pendingSession(): Pair<Long, Long>? {
        val prefs = context.dataStore.data.first()
        val game = prefs[Keys.sessionGame] ?: return null
        val start = prefs[Keys.sessionStart] ?: return null
        return game to start
    }

    suspend fun setPendingSession(gameId: Long?, start: Long) = edit {
        if (gameId == null) {
            it.remove(Keys.sessionGame); it.remove(Keys.sessionStart)
        } else {
            it[Keys.sessionGame] = gameId; it[Keys.sessionStart] = start
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
