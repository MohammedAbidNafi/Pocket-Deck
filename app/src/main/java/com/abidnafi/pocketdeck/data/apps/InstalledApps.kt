package com.abidnafi.pocketdeck.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    val installer: String?,
) {
    val fromPlayStore: Boolean get() = installer == PLAY_STORE

    companion object {
        const val PLAY_STORE = "com.android.vending"
    }
}

/** Reads launchable apps from the PackageManager and reports install / uninstall changes. */
class InstalledApps(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager

    /** Every app with a phone or TV launcher entry, except this one. */
    @Suppress("DEPRECATION")
    fun query(): List<InstalledApp> {
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        )
        val packages = intents.flatMap { pm.queryIntentActivities(it, 0) }
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .toSet()
        return packages.mapNotNull { pkg ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            InstalledApp(
                packageName = pkg,
                label = info.loadLabel(pm).toString(),
                isGame = isGame(info),
                installer = installerOf(pkg),
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun isInstalled(pkg: String): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess

    fun label(pkg: String): String? =
        runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrNull()

    fun launchIntent(pkg: String): Intent? =
        pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg)

    @Suppress("DEPRECATION")
    private fun isGame(info: ApplicationInfo): Boolean =
        info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    @Suppress("DEPRECATION")
    private fun installerOf(pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            pm.getInstallerPackageName(pkg)
        }
    }.getOrNull()

    /** Calls [onChange] whenever a package is added, removed or replaced. Returns an unregister function. */
    fun watch(onChange: () -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return { context.unregisterReceiver(receiver) }
    }
}
