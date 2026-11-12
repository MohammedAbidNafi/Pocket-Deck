package com.abidnafi.pocketdeck.data

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Another installed home app (Pixel Launcher, Google TV home, Nova…). */
data class OtherLauncher(val packageName: String, val activity: String, val label: String)

/**
 * [available]: the "use as home screen" option is switched on, so Android lists PocketDeck as a home app.
 * [isDefault]: PocketDeck is the home app right now (pressing Home opens it).
 */
data class HomeState(val available: Boolean = false, val isDefault: Boolean = false)

/**
 * Optional "PocketDeck as your home screen". The HOME intent filter lives on a manifest
 * activity-alias (`.HomeEntry`) that ships disabled, so PocketDeck only shows up as a home app
 * after the user opts in, and disappears again when they opt out.
 */
class HomeMode(private val context: Context) {
    private val pm = context.packageManager
    private val alias = ComponentName(context, "${context.packageName}.HomeEntry")

    private val _state = MutableStateFlow(read())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _homePresses = MutableStateFlow(0)
    /** Goes up each time Home is pressed while PocketDeck is already open: jump back to the Home tab. */
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    fun onHomePressed() = _homePresses.update { it + 1 }

    /** Re-reads the system state (call on resume: the user may have changed the default elsewhere). */
    fun refresh() {
        _state.value = read()
    }

    private fun read() = HomeState(available = isAliasEnabled(), isDefault = isDefaultHome())

    private fun isAliasEnabled(): Boolean =
        pm.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** Android 10+: ask the role system (authoritative). Older: see what a Home press resolves to. */
    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
        return pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
    }

    /** Lists PocketDeck as a home app (or stops listing it). Leaving home mode hands Home back to Android. */
    fun setAvailable(on: Boolean) {
        pm.setComponentEnabledSetting(
            alias,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        refresh()
    }

    /**
     * The system screen that makes PocketDeck the default home app: the "default home app" prompt
     * on Android 10+, otherwise the default-apps settings page.
     */
    fun makeDefaultIntent(): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return homeSettingsIntent()
    }

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    /** Every other home app, minus the system's "no launcher yet" fallback screen. */
    fun otherLaunchers(): List<OtherLauncher> =
        pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && !it.name.endsWith("FallbackHome") }
            .map { OtherLauncher(it.packageName, it.name, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

    /** The launcher "Switch to Android home" opens: the user's pick, else the only other one, else null (ask). */
    fun preferredLauncher(preferredPackage: String?): OtherLauncher? {
        val others = otherLaunchers()
        return others.firstOrNull { it.packageName == preferredPackage } ?: others.singleOrNull()
    }

    /**
     * Opens another launcher's home screen as an ordinary app. It must NOT be started with the HOME
     * category: Android would then treat it as an active home screen and the Home key would keep
     * returning to it instead of PocketDeck.
     */
    fun open(activity: Activity, launcher: OtherLauncher): Boolean = try {
        activity.startActivity(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(launcher.packageName, launcher.activity))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
