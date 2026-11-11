package com.abidnafi.pocketdeck.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.BuildConfig
import com.abidnafi.pocketdeck.R
import com.abidnafi.pocketdeck.ui.AppLogo
import com.abidnafi.pocketdeck.ui.LocalMessages
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.SectionHeader
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette

const val DEVELOPER_NAME = "Abid Nafi"
private const val DEVELOPER_SITE = "https://abidnafi.com"
private const val DEVELOPER_GITHUB = "https://github.com/MohammedAbidNafi"

private const val APACHE_2 = "Apache License 2.0"
private const val APACHE_2_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val CC_BY_3_URL = "https://creativecommons.org/licenses/by/3.0/"

/** One open-source component bundled in the app. */
private data class Library(val name: String, val author: String, val license: String, val url: String)

private val Libraries = listOf(
    Library("Jetpack Compose (UI, Foundation, Animation, Material 3)", "The Android Open Source Project", APACHE_2, "https://github.com/androidx/androidx"),
    Library("AndroidX Activity, Core & Lifecycle", "The Android Open Source Project", APACHE_2, "https://github.com/androidx/androidx"),
    Library("AndroidX Navigation", "The Android Open Source Project", APACHE_2, "https://github.com/androidx/androidx"),
    Library("AndroidX Room", "The Android Open Source Project", APACHE_2, "https://github.com/androidx/androidx"),
    Library("AndroidX DataStore", "The Android Open Source Project", APACHE_2, "https://github.com/androidx/androidx"),
    Library("AndroidX DocumentFile & Palette", "The Android Open Source Project", APACHE_2, "https://github.com/androidx/androidx"),
    Library("Material Icons", "Google", APACHE_2, "https://github.com/google/material-design-icons"),
    Library("Coil", "Coil Contributors", APACHE_2, "https://github.com/coil-kt/coil"),
    Library("OkHttp", "Square, Inc.", APACHE_2, "https://github.com/square/okhttp"),
    Library("Okio", "Square, Inc.", APACHE_2, "https://github.com/square/okio"),
    Library("Kotlin standard library", "JetBrains s.r.o. and Kotlin contributors", APACHE_2, "https://github.com/JetBrains/kotlin"),
    Library("kotlinx.coroutines", "JetBrains s.r.o. and contributors", APACHE_2, "https://github.com/Kotlin/kotlinx.coroutines"),
    Library("kotlinx.serialization", "JetBrains s.r.o. and contributors", APACHE_2, "https://github.com/Kotlin/kotlinx.serialization"),
)

/** About page: who made the app, open-source credits, artwork attributions and trademarks. */
@Composable
fun AboutScreen(nav: NavController) {
    val context = LocalContext.current
    val messages = LocalMessages.current
    val memory = rememberFocusMemory()
    val first = remember { FocusRequester() }
    val appName = stringResource(R.string.app_name)

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            messages.show("No browser found", url)
        }
    }

    fun key(k: String): Pair<FocusRequester, (Boolean) -> Unit> =
        memory.requester(k) to { focused: Boolean -> if (focused) memory.lastKey = k }

    SettingsPage("About") {
        Header(appName)

        SectionHeader("Made by")
        SettingRow(
            title = DEVELOPER_NAME,
            subtitle = "Design, development and the $appName logo",
            value = "abidnafi.com",
            icon = Icons.Rounded.Favorite,
            onClick = { open(DEVELOPER_SITE) },
            focusRequester = first,
        )
        key("github").let { (r, f) ->
            SettingRow(
                title = "GitHub",
                subtitle = "More projects by $DEVELOPER_NAME",
                value = "MohammedAbidNafi",
                icon = Icons.Rounded.Code,
                onClick = { open(DEVELOPER_GITHUB) },
                focusRequester = r, onFocusChange = f,
            )
        }

        SectionHeader("Artwork")
        key("robot").let { (r, f) ->
            SettingRow(
                title = "Android robot",
                subtitle = "The Android robot is reproduced or modified from work created and shared by Google " +
                    "and used according to terms described in the Creative Commons 3.0 Attribution License.",
                value = "CC BY 3.0",
                icon = Icons.Rounded.Android,
                onClick = { open(CC_BY_3_URL) },
                focusRequester = r, onFocusChange = f,
            )
        }
        key("thumbs").let { (r, f) ->
            SettingRow(
                title = "Game box art",
                subtitle = "Downloaded on demand from the libretro-thumbnails project. Each image belongs to its respective owner.",
                value = "libretro",
                icon = Icons.Rounded.Image,
                onClick = { open("https://github.com/libretro-thumbnails/libretro-thumbnails") },
                focusRequester = r, onFocusChange = f,
            )
        }

        SectionHeader("Open-source libraries")
        Libraries.forEach { lib ->
            key("lib/${lib.name}").let { (r, f) ->
                SettingRow(
                    title = lib.name,
                    subtitle = "© ${lib.author}",
                    value = lib.license,
                    icon = Icons.Rounded.Code,
                    onClick = { open(lib.url) },
                    focusRequester = r, onFocusChange = f,
                )
            }
        }

        SectionHeader("Licences")
        key("apache").let { (r, f) ->
            SettingRow(
                APACHE_2, { open(APACHE_2_URL) }, icon = Icons.AutoMirrored.Rounded.OpenInNew,
                subtitle = "Covers every library listed above", focusRequester = r, onFocusChange = f,
            )
        }
        key("ccby").let { (r, f) ->
            SettingRow(
                "Creative Commons Attribution 3.0", { open(CC_BY_3_URL) }, icon = Icons.AutoMirrored.Rounded.OpenInNew,
                subtitle = "Covers the Android robot", focusRequester = r, onFocusChange = f,
            )
        }

        SectionHeader("Trademarks")
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Rounded.Gavel, null, tint = Palette.TextDim, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                "Android is a trademark of Google LLC. Xbox, PlayStation and Nintendo, and their controller button " +
                    "layouts, are trademarks of their respective owners, as are the names of the emulators and games " +
                    "shown in the app. $appName is an independent project and is not affiliated with or endorsed by any of them.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
            )
        }
    }
    InitialFocus(memory, first)
}

@Composable
private fun Header(appName: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(Modifier.size(112.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(appName, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Interests, null, tint = LocalAccent.current, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Made with love by $DEVELOPER_NAME", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            }
        }
    }
}
