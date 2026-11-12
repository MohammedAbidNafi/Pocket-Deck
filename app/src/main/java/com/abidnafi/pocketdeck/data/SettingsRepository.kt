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

/** Stream resolution; [NATIVE] uses the device's own screen size. */
enum class StreamResolution(val label: String, val width: Int, val height: Int) {
    P720("720p", 1280, 720),
    P1080("1080p", 1920, 1080),
    P1440("1440p", 2560, 1440),
    P2160("4K", 3840, 2160),
    NATIVE("Native", 0, 0),
}

enum class StreamCodec(val label: String) { AUTO("Automatic"), H264("H.264"), HEVC("HEVC (H.265)"), AV1("AV1") }

enum class StreamAudio(val label: String) { STEREO("Stereo"), SURROUND_51("5.1 surround"), SURROUND_71("7.1 surround") }

enum class FramePacing(val label: String) {
    LATENCY("Lowest latency"),
    BALANCED("Balanced"),
    CAP_FPS("Cap to frame rate"),
    SMOOTHNESS("Smoothest video"),
}

/** PC streaming quality and behaviour. [bitrateKbps] 0 = pick from resolution and frame rate. */
data class StreamSettings(
    val resolution: StreamResolution = StreamResolution.P1080,
    val fps: Int = 60,
    val bitrateKbps: Int = 0,
    val codec: StreamCodec = StreamCodec.AUTO,
    val hdr: Boolean = false,
    val audio: StreamAudio = StreamAudio.STEREO,
    val framePacing: FramePacing = FramePacing.BALANCED,
    val showStats: Boolean = false,
    /** Keep sound playing on the PC too. */
    val playHostAudio: Boolean = false,
    /** Let the host change its resolution to match the stream. */
    val optimizeHostSettings: Boolean = true,
    /** Touchscreen moves the mouse like a laptop trackpad (off = tap where you point). */
    val touchAsTrackpad: Boolean = true,
    /** Quit the game on the PC when disconnecting from the in-stream menu by default. */
    val quitOnDisconnect: Boolean = false,
) {
    companion object {
        val FPS_OPTIONS = listOf(30, 60, 90, 120)
        /** 0 = automatic. Values in kbps. */
        val BITRATE_OPTIONS = listOf(0, 5_000, 10_000, 20_000, 30_000, 40_000, 60_000, 80_000, 100_000, 150_000)
    }
}

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
    val stream: StreamSettings = StreamSettings(),
    /** Collections PocketDeck fills by itself (Android, PC, each system…). */
    val autoCollections: Boolean = true,
    /** Auto collection ids the user hid. */
    val hiddenAutoCollections: Set<String> = emptySet(),
    val collectionSort: CollectionSort = CollectionSort.NAME,
    /** Collection key (`c:<id>` or an auto collection id) → [GameSort] name. */
    val collectionGameSorts: Map<String, String> = emptyMap(),
    /** Home-screen mode: the Android launcher "Switch to Android home" opens (package name). */
    val androidLauncher: String? = null,
) {
    fun gameSortFor(collectionKey: String): GameSort =
        collectionGameSorts[collectionKey]?.let { n -> GameSort.entries.firstOrNull { it.name == n } } ?: GameSort.NAME
}

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

        val streamResolution = stringPreferencesKey("stream_resolution")
        val streamFps = intPreferencesKey("stream_fps")
        val streamBitrate = intPreferencesKey("stream_bitrate")
        val streamCodec = stringPreferencesKey("stream_codec")
        val streamHdr = booleanPreferencesKey("stream_hdr")
        val streamAudio = stringPreferencesKey("stream_audio")
        val streamPacing = stringPreferencesKey("stream_pacing")
        val streamStats = booleanPreferencesKey("stream_stats")
        val streamHostAudio = booleanPreferencesKey("stream_host_audio")
        val streamSops = booleanPreferencesKey("stream_sops")
        val streamTrackpad = booleanPreferencesKey("stream_trackpad")
        val streamQuitOnDisconnect = booleanPreferencesKey("stream_quit_on_disconnect")

        val autoCollections = booleanPreferencesKey("auto_collections")
        val hiddenAutoCollections = stringSetPreferencesKey("auto_collections_hidden")
        val collectionSort = stringPreferencesKey("collection_sort")
        val collectionGameSorts = stringPreferencesKey("collection_game_sorts")
        val androidLauncher = stringPreferencesKey("android_launcher")
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
        stream = StreamSettings(
            resolution = enumOr(this[Keys.streamResolution], StreamResolution.P1080),
            fps = this[Keys.streamFps] ?: 60,
            bitrateKbps = this[Keys.streamBitrate] ?: 0,
            codec = enumOr(this[Keys.streamCodec], StreamCodec.AUTO),
            hdr = this[Keys.streamHdr] ?: false,
            audio = enumOr(this[Keys.streamAudio], StreamAudio.STEREO),
            framePacing = enumOr(this[Keys.streamPacing], FramePacing.BALANCED),
            showStats = this[Keys.streamStats] ?: false,
            playHostAudio = this[Keys.streamHostAudio] ?: false,
            optimizeHostSettings = this[Keys.streamSops] ?: true,
            touchAsTrackpad = this[Keys.streamTrackpad] ?: true,
            quitOnDisconnect = this[Keys.streamQuitOnDisconnect] ?: false,
        ),
        autoCollections = this[Keys.autoCollections] ?: true,
        hiddenAutoCollections = this[Keys.hiddenAutoCollections] ?: emptySet(),
        collectionSort = enumOr(this[Keys.collectionSort], CollectionSort.NAME),
        collectionGameSorts = this[Keys.collectionGameSorts]
            ?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() } ?: emptyMap(),
        androidLauncher = this[Keys.androidLauncher],
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

    suspend fun setAndroidLauncher(pkg: String?) = edit { if (pkg == null) it.remove(Keys.androidLauncher) else it[Keys.androidLauncher] = pkg }
    suspend fun setAutoCollections(value: Boolean) = edit { it[Keys.autoCollections] = value }
    suspend fun setAutoCollectionHidden(id: String, hidden: Boolean) = edit { prefs ->
        val set = prefs[Keys.hiddenAutoCollections].orEmpty()
        prefs[Keys.hiddenAutoCollections] = if (hidden) set + id else set - id
    }
    suspend fun showAllAutoCollections() = edit { it.remove(Keys.hiddenAutoCollections) }
    suspend fun setCollectionSort(sort: CollectionSort) = edit { it[Keys.collectionSort] = sort.name }
    suspend fun setCollectionGameSort(collectionKey: String, sort: GameSort) = edit { prefs ->
        val map = prefs.toSettings().collectionGameSorts.toMutableMap()
        if (sort == GameSort.NAME) map.remove(collectionKey) else map[collectionKey] = sort.name
        prefs[Keys.collectionGameSorts] = json.encodeToString(mapSerializer, map)
    }

    /** Saves every streaming setting at once (the screen edits a copy of [StreamSettings]). */
    suspend fun setStream(s: StreamSettings) = edit {
        it[Keys.streamResolution] = s.resolution.name
        it[Keys.streamFps] = s.fps
        it[Keys.streamBitrate] = s.bitrateKbps
        it[Keys.streamCodec] = s.codec.name
        it[Keys.streamHdr] = s.hdr
        it[Keys.streamAudio] = s.audio.name
        it[Keys.streamPacing] = s.framePacing.name
        it[Keys.streamStats] = s.showStats
        it[Keys.streamHostAudio] = s.playHostAudio
        it[Keys.streamSops] = s.optimizeHostSettings
        it[Keys.streamTrackpad] = s.touchAsTrackpad
        it[Keys.streamQuitOnDisconnect] = s.quitOnDisconnect
    }

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
