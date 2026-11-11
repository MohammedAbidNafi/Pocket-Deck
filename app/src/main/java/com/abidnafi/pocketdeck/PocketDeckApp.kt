package com.abidnafi.pocketdeck

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import com.abidnafi.pocketdeck.widget.GamesWidget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

class PocketDeckApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        with(container) {
            // Keep custom emulators visible to the registry.
            appScope.launch {
                settings.settings.map { it.customEmulators }.distinctUntilChanged().collect { emulators.custom = it }
            }
            installedApps.watch { appScope.launch { library.syncInstalledApps() } }
            // Home-screen widgets mirror the library: redraw them when games, collections,
            // or the look (accent / tile shape) change. Debounced so a rescan redraws once.
            appScope.launch {
                combine(
                    library.games,
                    library.collections,
                    library.memberships,
                    settings.settings.map { Triple(it.accentIndex, it.tileShape, it.onlineArtwork) }.distinctUntilChanged(),
                ) { _, _, _, _ -> Unit }
                    .drop(1)
                    .debounce(1500)
                    .collect { runCatching { GamesWidget.refreshAll(this@PocketDeckApp) } }
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
