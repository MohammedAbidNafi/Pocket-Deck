# PocketDeck

A controller-first game launcher for Android phones, handhelds and Android TV. PocketDeck pulls your ROMs, emulators and installed Android games into one couch-friendly library with box art, collections and play-time tracking, then hands each game off to the right emulator.

PocketDeck is a normal app, not a home-screen replacement. You open it like any other app (or from its Quick Settings tile, widget or controller shortcut), and your usual launcher stays as it is.

## Features

- **One library for everything:** ROMs from folders you pick, Android games you have installed, and any other app you choose to add.
- **ROM scanning through the Storage Access Framework.** You pick a folder, and PocketDeck works out the system from the file extension and the folder names (picking `…/ROMs/snes` maps to SNES). It doesn't need broad storage permissions.
- **Emulator handoff:** each system gets a default emulator, and launch settings can be changed per system. Games open with the right intent, extras and core.
- **Box art** is downloaded on demand from [libretro-thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails) and cached on the device. App icons are used for Android games.
- **Built for controllers:** D-pad and analog stick navigation, focus memory, an on-screen keyboard for search, and Xbox / PlayStation / Nintendo button glyphs. Touch works too.
- **Home rows:** last played, recently added, favourites, per-system rows and your own collections. You can reorder or hide any of them.
- **Play stats:** play sessions are timed from launch until you come back to PocketDeck.
- **Ways to get back to PocketDeck:**
  - an optional "Back to PocketDeck" notification while a game is running
  - a Quick Settings tile
  - an opt-in accessibility shortcut: hold **Select + Start** (View + Menu on Xbox) in any game. It only watches controller buttons and passes them through unchanged.
- **Home-screen widget** (Glance) that shows last played, recently added, favourites, all games or a collection.
- **Extras:** guided onboarding, UI sounds and haptics, an optional clock, and accent theming.

## Supported systems

NES · SNES · N64 · Game Boy · Game Boy Color · Game Boy Advance · DS · 3DS · GameCube · Wii · PS1 · PS2 · PSP · Genesis / Mega Drive · Sega CD · 32X · Master System · Game Gear · Saturn · Dreamcast · PC Engine · Neo Geo Pocket · Atari 2600 · Arcade

Systems are defined in [`app/src/main/assets/systems.json`](app/src/main/assets/systems.json). Each entry lists the system's extensions, folder aliases, libretro thumbnail set and default RetroArch core.

## Supported emulators

RetroArch · Dolphin · PPSSPP · DuckStation · AetherSX2 / NetherSX2 · melonDS · DraStic · Azahar · Mupen64Plus FZ · Redream · Flycast · Yaba Sanshiro 2 · MAME4droid · Eden / Citron · ScummVM · Lemuroid

Launch templates live in [`app/src/main/assets/emulators.json`](app/src/main/assets/emulators.json). A template gives the package names, the activity, the intent action, and the extras or data URI to send, using placeholders such as `{rom_uri}`, `{rom_path}`, `{package}` and `{core}`. To support a new emulator, you usually only need to add an entry here.

> **RetroArch note:** RetroArch needs its own *All files access* permission, and the core for each system has to be downloaded inside RetroArch first.

## Requirements

- Android 8.0 (API 26) or newer, targeting API 36
- Runs in landscape, on phones, handhelds and Android TV. A touchscreen, gamepad and Leanback are all optional.

## Install

A prebuilt, side-loadable APK is in [`release/PocketDeck-0.1.0.apk`](release/PocketDeck-0.1.0.apk). It is signed with the debug key, so it is meant for testing.

```bash
adb install release/PocketDeck-0.1.0.apk
```

## Building from source

You need JDK 17 or newer and the Android SDK (compile SDK 37). Opening the project in a recent Android Studio is the easiest route.

```bash
./gradlew assembleDebug
```

```bash
./gradlew assembleRelease
```

On Windows, use `gradlew.bat`. Release builds are minified and resource-shrunk with R8.

## Tech stack

- Kotlin, Jetpack Compose (Material 3) and Navigation Compose
- Room (via KSP) for the library, collections and play stats; DataStore for settings
- Coil 3 + OkHttp for artwork; AndroidX Palette for colours taken from the artwork
- Glance for the home-screen widget
- kotlinx.serialization for the system and emulator definitions

## Project layout

```
app/src/main/java/com/abidnafi/pocketdeck/
├── data/          # Library repository, settings, game launching, play-session tracking
│   ├── apps/      # Installed-app discovery and package change watching
│   ├── art/       # libretro thumbnails and app icons
│   ├── db/        # Room database, entities, DAOs
│   ├── emulators/ # Emulator registry and launch-intent templates
│   ├── model/     # Platform and emulator definitions
│   └── roms/      # SAF folder scanning and ROM name cleanup
├── input/         # Controller mapping, analog-stick navigation, sound and haptic feedback
├── shortcuts/     # Quick Settings tile and controller accessibility shortcut
├── ui/            # Compose screens: home, library, details, search, collections, settings, onboarding
└── widget/        # Glance home-screen widget and its setup and launch activities
```

## Credits

Designed and developed by [Abid Nafi](https://abidnafi.com) ([GitHub](https://github.com/MohammedAbidNafi)).

- Box art comes from the [libretro-thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails) project. Each image belongs to its respective owner.
- The Android robot is reproduced or modified from work created and shared by Google, and used under the [Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).
- Open-source libraries (AndroidX, Jetpack Compose, Coil, OkHttp, Okio, Kotlin, kotlinx) are used under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Disclaimer

PocketDeck doesn't include any games, ROMs, BIOS files or emulators. Only use games you legally own.

Android is a trademark of Google LLC. Xbox, PlayStation and Nintendo, and their controller layouts, are trademarks of their respective owners, as are the emulator and game names shown in the app. PocketDeck is an independent project and isn't affiliated with or endorsed by any of them.
